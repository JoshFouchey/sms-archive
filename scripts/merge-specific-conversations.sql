-- ============================================================================
-- MERGE SPECIFIC CONVERSATIONS - Manual targeted merge
-- ============================================================================
-- Use this script to merge specific conversations you've identified as duplicates
--
-- INSTRUCTIONS:
-- 1. Run preview-duplicate-conversations.sql first to identify duplicates
-- 2. Update the conversation IDs below in the CONFIGURATION section
-- 3. Backup your database first!
-- 4. Run this script
--
-- USAGE:
--   psql -U sms_user -d sms_archive -f scripts/merge-specific-conversations.sql
--
-- Or from docker:
--   docker exec -i sms-archive-db-1 psql -U sms_user -d sms_archive < scripts/merge-specific-conversations.sql
-- ============================================================================

BEGIN;

-- ============================================================================
-- CONFIGURATION - UPDATE THESE VALUES
-- ============================================================================
-- Set the primary conversation (the one to keep)
\set PRIMARY_CONVERSATION_ID 1

-- Set the duplicate conversation IDs (ones to merge into primary)
-- Example: ARRAY[2, 3, 4] to merge conversations 2, 3, and 4 into conversation 1
CREATE TEMP TABLE conversations_to_merge AS
SELECT unnest(ARRAY[2, 3]::BIGINT[]) as duplicate_id;
-- ============================================================================

-- Validation: Check if conversations exist
DO $$
DECLARE
    v_primary_id BIGINT := :PRIMARY_CONVERSATION_ID;
    v_primary_exists BOOLEAN;
    v_duplicate_count INTEGER;
BEGIN
    -- Check primary exists
    SELECT EXISTS(SELECT 1 FROM conversations WHERE id = v_primary_id) INTO v_primary_exists;
    
    IF NOT v_primary_exists THEN
        RAISE EXCEPTION 'Primary conversation ID % does not exist!', v_primary_id;
    END IF;
    
    -- Check duplicates exist
    SELECT COUNT(*) INTO v_duplicate_count
    FROM conversations_to_merge ctm
    WHERE EXISTS(SELECT 1 FROM conversations c WHERE c.id = ctm.duplicate_id);
    
    IF v_duplicate_count = 0 THEN
        RAISE EXCEPTION 'None of the duplicate conversation IDs exist!';
    END IF;
    
    RAISE NOTICE 'Validation passed: Primary exists, % duplicates found', v_duplicate_count;
END $$;

-- Show what we're about to merge
SELECT
    :PRIMARY_CONVERSATION_ID as "PRIMARY (Keeping)",
    c.name as "Name",
    (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = :PRIMARY_CONVERSATION_ID) as "Current Messages"
FROM conversations c
WHERE c.id = :PRIMARY_CONVERSATION_ID;

SELECT
    ctm.duplicate_id as "DUPLICATE (Merging)",
    c.name as "Name",
    COUNT(m.id) as "Messages to Move"
FROM conversations_to_merge ctm
JOIN conversations c ON c.id = ctm.duplicate_id
LEFT JOIN messages m ON m.conversation_id = c.id
GROUP BY ctm.duplicate_id, c.name
ORDER BY ctm.duplicate_id;

-- Step 1: Move messages from duplicates to primary
DO $$
DECLARE
    v_primary_id BIGINT := :PRIMARY_CONVERSATION_ID;
    moved_count INTEGER := 0;
    duplicate_deleted INTEGER := 0;
    msg_record RECORD;
BEGIN
    RAISE NOTICE 'Starting message migration...';
    
    -- Process each message from duplicate conversations
    FOR msg_record IN
        SELECT m.id
        FROM messages m
        JOIN conversations_to_merge ctm ON m.conversation_id = ctm.duplicate_id
        WHERE m.conversation_id != v_primary_id
    LOOP
        BEGIN
            -- Try to update the message's conversation
            UPDATE messages
            SET conversation_id = v_primary_id
            WHERE id = msg_record.id;
            
            moved_count := moved_count + 1;
        EXCEPTION
            WHEN unique_violation THEN
                -- If it's a duplicate message, delete it
                DELETE FROM messages WHERE id = msg_record.id;
                duplicate_deleted := duplicate_deleted + 1;
        END;
    END LOOP;
    
    RAISE NOTICE 'Moved % messages, deleted % duplicates', moved_count, duplicate_deleted;
END $$;

-- Step 2: Update last_message_at for primary conversation
UPDATE conversations c
SET last_message_at = (
    SELECT MAX(m.timestamp)
    FROM messages m
    WHERE m.conversation_id = c.id
)
WHERE c.id = :PRIMARY_CONVERSATION_ID;

-- Step 3: Merge participant lists (if any new participants)
INSERT INTO conversation_contacts (conversation_id, contact_id)
SELECT DISTINCT
    :PRIMARY_CONVERSATION_ID,
    cc.contact_id
FROM conversations_to_merge ctm
JOIN conversation_contacts cc ON cc.conversation_id = ctm.duplicate_id
WHERE NOT EXISTS (
    SELECT 1 FROM conversation_contacts cc2
    WHERE cc2.conversation_id = :PRIMARY_CONVERSATION_ID
    AND cc2.contact_id = cc.contact_id
);

-- Step 4: Delete duplicate conversation_contacts entries
DELETE FROM conversation_contacts cc
USING conversations_to_merge ctm
WHERE cc.conversation_id = ctm.duplicate_id;

-- Step 5: Delete duplicate conversations
DELETE FROM conversations c
USING conversations_to_merge ctm
WHERE c.id = ctm.duplicate_id;

-- Final summary
SELECT
    'Merge complete!' as "Status",
    :PRIMARY_CONVERSATION_ID as "Primary Conversation ID",
    (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = :PRIMARY_CONVERSATION_ID) as "Total Messages",
    (SELECT COUNT(*) FROM conversations_to_merge) as "Conversations Merged";

COMMIT;

-- ============================================================================
-- If you want to preview without committing, replace COMMIT with ROLLBACK
-- To rollback: Change COMMIT above to ROLLBACK
-- ============================================================================
