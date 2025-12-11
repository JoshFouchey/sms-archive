BEGIN;

-- 1. Build participant signatures for each conversation
CREATE TEMP TABLE conversation_signatures AS
SELECT 
    c.id AS conversation_id,
    c.user_id,
    STRING_AGG(cc.contact_id::text, ',' ORDER BY cc.contact_id) AS participant_signature
FROM conversations c
JOIN conversation_contacts cc ON cc.conversation_id = c.id
GROUP BY c.id, c.user_id;

-- 2. Identify groups of conversations with identical participant sets
CREATE TEMP TABLE duplicate_conversation_groups AS
SELECT
    MIN(conversation_id) AS canonical_id,
    ARRAY_AGG(conversation_id ORDER BY conversation_id) AS all_ids,
    COUNT(*) AS count
FROM conversation_signatures
GROUP BY user_id, participant_signature
HAVING COUNT(*) > 1;

-- 3. Merge messages from duplicates into canonical conversation
INSERT INTO messages (conversation_id, body, "timestamp", direction, user_id, message_source_id)
SELECT
    dcg.canonical_id AS conversation_id,
    m.body,
    m."timestamp",
    m.direction,
    m.user_id,
    m.id AS message_source_id
FROM duplicate_conversation_groups dcg
JOIN messages m ON m.conversation_id = ANY(dcg.all_ids)
WHERE m.conversation_id != dcg.canonical_id
ON CONFLICT ON CONSTRAINT unique_message_per_conversation DO NOTHING;

-- 4. Update attachments to point to the new merged message IDs
UPDATE attachments a
SET message_id = new_m.id
FROM messages src
JOIN messages new_m ON new_m.message_source_id = src.id
WHERE a.message_id = src.id;

-- 5. Delete all duplicate conversations except canonical
DELETE FROM conversations c
USING duplicate_conversation_groups dcg
WHERE c.id = ANY(dcg.all_ids)
  AND c.id != dcg.canonical_id;

COMMIT;
