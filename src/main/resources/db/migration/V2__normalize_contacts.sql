-- V2: Normalize and deduplicate contacts (simplified)
-- Drop unique index, compute winners, remap FKs, delete losers, recreate index.

DROP INDEX IF EXISTS ux_contacts_user_normalized;

-- Temp table of contact cores
CREATE TEMP TABLE tmp_contact_core AS
SELECT c.id,
       c.user_id,
       c.normalized_number,
       CASE
         WHEN regexp_replace(c.normalized_number, '\\D', '') ~ '^[1]?[0-9]{10}$' THEN right(regexp_replace(c.normalized_number, '\\D', ''), 10)
         ELSE regexp_replace(c.normalized_number, '\\D', '')
       END AS core_digits
FROM contacts c;

-- Determine winners (prefer +1 form, else smallest id)
CREATE TEMP TABLE tmp_contact_winners AS
SELECT DISTINCT ON (user_id, core_digits)
       id AS keep_id,
       user_id,
       core_digits,
       normalized_number
FROM tmp_contact_core
ORDER BY user_id, core_digits,
         CASE WHEN normalized_number LIKE '+1%' THEN 0 ELSE 1 END,
         id;

-- Normalize winner numbers to +1xxxxxxxxxx when NANP core_digits length=10
UPDATE contacts c SET normalized_number = '+1' || w.core_digits
FROM tmp_contact_winners w
WHERE c.id = w.keep_id
  AND length(w.core_digits) = 10
  AND c.normalized_number <> '+1' || w.core_digits;

-- Remap messages sender_contact_id
UPDATE messages m SET sender_contact_id = w.keep_id
FROM tmp_contact_core tc
JOIN tmp_contact_winners w USING (user_id, core_digits)
WHERE m.sender_contact_id = tc.id
  AND tc.id <> w.keep_id;

-- Remap conversation_contacts
UPDATE conversation_contacts cc SET contact_id = w.keep_id
FROM tmp_contact_core tc
JOIN tmp_contact_winners w USING (user_id, core_digits)
WHERE cc.contact_id = tc.id
  AND tc.id <> w.keep_id;

-- Delete loser contacts
DELETE FROM contacts c
USING tmp_contact_core tc
JOIN tmp_contact_winners w USING (user_id, core_digits)
WHERE c.id = tc.id
  AND tc.id <> w.keep_id;

-- Recreate unique index
CREATE UNIQUE INDEX ux_contacts_user_normalized ON contacts (user_id, normalized_number);

-- Cleanup temp tables (optional; they disappear at session end)
DROP TABLE IF EXISTS tmp_contact_core;
DROP TABLE IF EXISTS tmp_contact_winners;
