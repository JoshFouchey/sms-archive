BEGIN;

-- 1. Identify conversation groups that share the same participant set
CREATE TEMP TABLE duplicate_conversation_groups AS
SELECT 
    array_agg(c.id ORDER BY c.id) AS conversation_ids,
    COUNT(*) AS count
FROM conversations c
GROUP BY c.participant_hash
HAVING COUNT(*) > 1;

-- 2. Pick the lowest conversation_id in each group as the “canonical”
CREATE TEMP TABLE canonical_conversations AS
SELECT 
    conversation_ids[1] AS canonical_id,
    conversation_ids      AS all_ids
FROM duplicate_conversation_groups;

-- 3. Merge messages from duplicates into their canonical conversation
--    Safe insert: skips duplicates using ON CONFLICT DO NOTHING
INSERT INTO messages (conversation_id, body, "timestamp", direction, user_id, message_source_id)
SELECT 
    cc.canonical_id AS conversation_id,
    m.body,
    m."timestamp",
    m.direction,
    m.user_id,
    m.id AS message_source_id
FROM canonical_conversations cc
JOIN messages m 
    ON m.conversation_id = ANY(cc.all_ids)
WHERE m.conversation_id != cc.canonical_id
ON CONFLICT ON CONSTRAINT unique_message_per_conversation DO NOTHING;

-- 4. Move attachments from duplicate messages
UPDATE attachments a
SET message_id = new_messages.id
FROM messages src
JOIN messages new_messages
    ON new_messages.message_source_id = src.id
WHERE a.message_id = src.id;

-- 5. Delete duplicate conversations (leave only canonical)
DELETE FROM conversations c
USING canonical_conversations cc
WHERE c.id = ANY(cc.all_ids)
  AND c.id != cc.canonical_id;

COMMIT;
