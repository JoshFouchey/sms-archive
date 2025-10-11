-- V2: Functional unique index to prevent duplicate messages
-- Uniqueness key: (contact_id, timestamp, msg_box, protocol, md5(lower(coalesce(body,''))))
-- Using md5 on normalized body keeps the index narrow while avoiding very large text entries.

-- Problem previously: a WITH (ranked) CTE was referenced in two separate DELETE statements.
-- In PostgreSQL a WITH CTE only applies to the immediately following statement, so the
-- second DELETE failed with "relation ranked does not exist". We simplify to a single
-- DELETE relying on ON DELETE CASCADE for message_parts.

-- 1. Delete duplicate messages (keep lowest id per dedupe key)
WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY contact_id, "timestamp", msg_box, protocol, md5(lower(coalesce(body,'')))
               ORDER BY id
           ) AS rn
    FROM messages
), dupes AS (
    SELECT id FROM ranked WHERE rn > 1
)
DELETE FROM messages m
USING dupes d
WHERE m.id = d.id;

-- 2. Create the unique index (idempotent)
CREATE UNIQUE INDEX IF NOT EXISTS ux_messages_dedupe
    ON messages (contact_id, "timestamp", msg_box, protocol, md5(lower(coalesce(body,''))));
