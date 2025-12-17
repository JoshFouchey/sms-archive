-- ============================================================================
-- MANUAL CONVERSATION MERGE
-- ============================================================================
-- Use this to manually merge two specific conversations
-- All messages from SOURCE conversation will be moved to TARGET conversation
-- Then SOURCE conversation will be deleted
--
-- USAGE:
--   1. Update the conversation IDs below
--   2. Run: docker exec -i sms-archive-db-1 psql -U sms_user -d sms_archive < scripts/manual-merge-conversations.sql
-- ============================================================================

-- ===== CONFIGURATION - UPDATE THESE VALUES =====
\set target_conversation_id 123  -- Keep this conversation (all messages will go here)
\set source_conversation_id 456  -- Delete this conversation (messages moved to target)
-- ==============================================

BEGIN;

-- Show what we're about to do
SELECT 
    'Merging conversation ' || :source_conversation_id || ' INTO conversation ' || :target_conversation_id as action;

-- Show details of both conversations BEFORE merge
SELECT 
    '=== BEFORE MERGE ===' as status,
    c.id as conversation_id,
    c.name,
    (SELECT COUNT(*) FROM messages WHERE conversation_id = c.id) as message_count,
    (SELECT STRING_AGG(COALESCE(cont.name, cont.number), ', ' ORDER BY cont.name)
     FROM conversation_contacts cc
     JOIN contacts cont ON cont.id = cc.contact_id
     WHERE cc.conversation_id = c.id) as participants
FROM conversations c
WHERE c.id IN (:target_conversation_id, :source_conversation_id)
ORDER BY c.id;

-- Move all messages from source to target
-- Handle potential duplicates by catching unique violations
DO $$
DECLARE
    moved_count INTEGER := 0;
    duplicate_count INTEGER := 0;
    msg_record RECORD;
BEGIN
    FOR msg_record IN
        SELECT id 
        FROM messages 
        WHERE conversation_id = :source_conversation_id
    LOOP
        BEGIN
            -- Try to move the message
            UPDATE messages
            SET conversation_id = :target_conversation_id
            WHERE id = msg_record.id;
            
            moved_count := moved_count + 1;
        EXCEPTION
            WHEN unique_violation THEN
                -- If it's a duplicate, delete it
                DELETE FROM messages WHERE id = msg_record.id;
                duplicate_count := duplicate_count + 1;
        END;
    END LOOP;
    
    RAISE NOTICE 'Moved % messages, deleted % duplicates', moved_count, duplicate_count;
END $$;

-- Update last_message_at for target conversation
UPDATE conversations
SET last_message_at = (
    SELECT MAX(timestamp)
    FROM messages
    WHERE conversation_id = :target_conversation_id
)
WHERE id = :target_conversation_id;

-- Delete conversation_contacts for source conversation
DELETE FROM conversation_contacts
WHERE conversation_id = :source_conversation_id;

-- Delete source conversation
DELETE FROM conversations
WHERE id = :source_conversation_id;

-- Show details AFTER merge
SELECT 
    '=== AFTER MERGE ===' as status,
    c.id as conversation_id,
    c.name,
    (SELECT COUNT(*) FROM messages WHERE conversation_id = c.id) as message_count,
    (SELECT STRING_AGG(COALESCE(cont.name, cont.number), ', ' ORDER BY cont.name)
     FROM conversation_contacts cc
     JOIN contacts cont ON cont.id = cc.contact_id
     WHERE cc.conversation_id = c.id) as participants
FROM conversations c
WHERE c.id = :target_conversation_id;

-- Confirm or rollback
-- Change to COMMIT if you want to keep the changes
ROLLBACK;
-- COMMIT;

-- ============================================================================
-- To actually apply the changes:
--   1. Verify the BEFORE/AFTER output looks correct
--   2. Change ROLLBACK to COMMIT above
--   3. Run the script again
-- ============================================================================
