-- Merges contacts that were duplicated because ux_contacts_user_normalized was
-- corrupted by an OS collation (glibc) change, then rebuilds the affected indexes.
-- The lowest contact id in each (user_id, normalized_number) group is kept.
--
-- Dry run (rolls back):  psql -v apply=0 ... < fix-duplicate-contacts.sql
-- Apply:                 psql -v apply=1 ... < fix-duplicate-contacts.sql
\set ON_ERROR_STOP on
BEGIN;

-- Corrupt indexes can hide duplicates, so find them with sequential scans only.
SET LOCAL enable_indexscan = off;
SET LOCAL enable_indexonlyscan = off;
SET LOCAL enable_bitmapscan = off;

CREATE TEMP TABLE contact_merge_map ON COMMIT DROP AS
SELECT c.id AS loser_id, g.winner_id
FROM contacts c
JOIN (SELECT user_id, normalized_number, min(id) AS winner_id
      FROM contacts
      GROUP BY user_id, normalized_number
      HAVING count(*) > 1) g USING (user_id, normalized_number)
WHERE c.id <> g.winner_id;

RESET enable_indexscan;
RESET enable_indexonlyscan;
RESET enable_bitmapscan;

SELECT count(*) AS contacts_to_merge FROM contact_merge_map;

UPDATE contacts w
SET name = l.name, updated_at = now()
FROM contact_merge_map mm JOIN contacts l ON l.id = mm.loser_id
WHERE w.id = mm.winner_id
  AND coalesce(trim(w.name), '') = ''
  AND coalesce(trim(l.name), '') <> '';

UPDATE messages m
SET sender_contact_id = mm.winner_id
FROM contact_merge_map mm
WHERE m.sender_contact_id = mm.loser_id;

INSERT INTO conversation_contacts (conversation_id, contact_id)
SELECT cc.conversation_id, mm.winner_id
FROM conversation_contacts cc
JOIN contact_merge_map mm ON mm.loser_id = cc.contact_id
ON CONFLICT DO NOTHING;

DELETE FROM conversation_contacts
WHERE contact_id IN (SELECT loser_id FROM contact_merge_map);

UPDATE contacts c
SET merged_into_id = mm.winner_id
FROM contact_merge_map mm
WHERE c.merged_into_id = mm.loser_id;

-- Knowledge-graph tables only exist on databases that have not yet run V18.
DO $$
BEGIN
  IF to_regclass('public.kg_entity_contact_links') IS NOT NULL THEN
    EXECUTE 'DELETE FROM kg_entity_contact_links
             WHERE contact_id IN (SELECT loser_id FROM contact_merge_map)';
  END IF;
END $$;

DELETE FROM contacts WHERE id IN (SELECT loser_id FROM contact_merge_map);

-- Rebuild every index on the tables whose text indexes were corrupted.
REINDEX TABLE contacts;
REINDEX TABLE conversations;

SELECT
  (SELECT count(*) FROM contacts) AS contacts_after,
  (SELECT count(*) FROM (SELECT 1 FROM contacts
     GROUP BY user_id, normalized_number HAVING count(*) > 1) d) AS duplicate_groups_after;

\if :apply
COMMIT;
\echo 'APPLIED'
\else
ROLLBACK;
\echo 'DRY RUN - rolled back, nothing changed'
\endif
