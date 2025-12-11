-- ============================================================================
-- MERGE DUPLICATE CONVERSATIONS - One-time cleanup script
-- ============================================================================
-- This script identifies and merges duplicate conversations (same participants)
-- Keeps the oldest conversation and moves all messages to it
--
-- USAGE:
--   1. Backup your database first!
--   2. Run: docker exec -i sms-archive-db-1 psql -U sms_user -d sms_archive < merge-duplicate-conversations.sql
--   3. Or connect to DB and run manually
--
-- WHAT IT DOES:
--   - Finds conversations with identical participant sets
--   - Keeps the oldest conversation (lowest ID)
--   - Moves all messages from duplicates to the primary conversation
--   - Updates last_message_at timestamp
--   - Deletes duplicate conversations
-- ============================================================================

BEGIN;

-- Step 1: Find duplicate conversations (same participants)
-- This creates a temp table with groups of duplicate conversation IDs
CREATE TEMP TABLE duplicate_conversation_groups AS
SELECT
    MIN(c.id) as primary_conversation_id,
    ARRAY_AGG(c.id ORDER BY c.id) as all_conversation_ids,
    c.user_id,
    STRING_AGG(cc.contact_id::TEXT, ',' ORDER BY cc.contact_id) as participant_signature,
    COUNT(DISTINCT c.id) as duplicate_count
FROM conversations c
         JOIN conversation_contacts cc ON c.id = cc.conversation_id
GROUP BY c.user_id, STRING_AGG(cc.contact_id::TEXT, ',' ORDER BY cc.contact_id)
HAVING COUNT(DISTINCT c.id) > 1;

-- Show what we found
SELECT
    'Found ' || COUNT(*) || ' groups of duplicate conversations affecting ' ||
    SUM(duplicate_count - 1) || ' conversations to be merged' as summary
FROM duplicate_conversation_groups;

-- Show details of duplicates
SELECT
    dcg.primary_conversation_id as "Keep (Primary)",
    dcg.all_conversation_ids as "Duplicate IDs",
    dcg.duplicate_count as "Total Dupes",
    c.name as "Conversation Name",
    COUNT(m.id) as "Messages in Primary"
FROM duplicate_conversation_groups dcg
         JOIN conversations c ON c.id = dcg.primary_conversation_id
         LEFT JOIN messages m ON m.conversation_id = c.id
GROUP BY dcg.primary_conversation_id, dcg.all_conversation_ids, dcg.duplicate_count, c.name
ORDER BY dcg.duplicate_count DESC;

-- Step 2: Move all messages from duplicate conversations to primary conversation
-- Handle potential duplicate messages by updating conversation_id one by one
-- The unique index will prevent actual duplicates from being created
DO $$
DECLARE
moved_count INTEGER := 0;
    duplicate_count INTEGER := 0;
    msg_record RECORD;
BEGIN
    -- Process each message from duplicate conversations
FOR msg_record IN
SELECT m.id, dcg.primary_conversation_id
FROM messages m
         JOIN duplicate_conversation_groups dcg ON m.conversation_id = ANY(dcg.all_conversation_ids)
WHERE m.conversation_id != dcg.primary_conversation_id
    LOOP
BEGIN
            -- Try to update the message's conversation
UPDATE messages
SET conversation_id = msg_record.primary_conversation_id
WHERE id = msg_record.id;

moved_count := moved_count + 1;
EXCEPTION
            WHEN unique_violation THEN
                -- If it's a duplicate (violates unique constraint), delete it
DELETE FROM messages WHERE id = msg_record.id;
duplicate_count := duplicate_count + 1;
END;
END LOOP;

    RAISE NOTICE 'Moved % messages, deleted % duplicates', moved_count, duplicate_count;
END $$;

-- Show total messages now in primary conversations
SELECT
    'Total messages now in primary conversations: ' || COUNT(*) as summary
FROM messages m
         JOIN duplicate_conversation_groups dcg ON m.conversation_id = dcg.primary_conversation_id;

-- Step 3: Update last_message_at for primary conversations
UPDATE conversations c
SET last_message_at = (
    SELECT MAX(m.timestamp)
    FROM messages m
    WHERE m.conversation_id = c.id
)
WHERE c.id IN (SELECT primary_conversation_id FROM duplicate_conversation_groups);

-- Step 4: Delete duplicate conversation_contacts entries
DELETE FROM conversation_contacts cc
    USING duplicate_conversation_groups dcg
WHERE cc.conversation_id = ANY(dcg.all_conversation_ids)
  AND cc.conversation_id != dcg.primary_conversation_id;

-- Step 5: Delete duplicate conversations
DELETE FROM conversations c
    USING duplicate_conversation_groups dcg
WHERE c.id = ANY(dcg.all_conversation_ids)
  AND c.id != dcg.primary_conversation_id;

-- Show final summary
SELECT
    'Cleanup complete! Merged ' || COUNT(*) || ' groups of duplicates' as summary
FROM duplicate_conversation_groups;

-- Show remaining conversations per user
SELECT
    u.username,
    COUNT(DISTINCT c.id) as conversation_count,
    SUM((SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id)) as total_messages
FROM users u
         LEFT JOIN conversations c ON c.user_id = u.id
GROUP BY u.username;

COMMIT;

-- ============================================================================
-- If you want to preview without committing, replace COMMIT with ROLLBACK
-- ============================================================================

