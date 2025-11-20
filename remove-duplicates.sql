-- SQL Script to Remove Duplicate Messages
-- This script identifies and removes duplicate messages that were imported before the fix
-- Run this against your database to clean up existing duplicates

-- Step 0: Count duplicates BEFORE deletion (run this first!)
WITH duplicate_check AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY
                user_id,
                conversation_id,
                timestamp,
                msg_box,
                protocol,
                LOWER(TRIM(COALESCE(body, '')))
            ORDER BY created_at ASC
        ) as row_num
    FROM messages
    WHERE conversation_id IS NOT NULL
)
SELECT
    COUNT(CASE WHEN row_num = 1 THEN 1 END) as unique_messages,
    COUNT(CASE WHEN row_num > 1 THEN 1 END) as duplicate_messages,
    COUNT(*) as total_messages
FROM duplicate_check;

-- Step 1: Identify duplicates (for review)
-- This query shows which messages are duplicates
SELECT
    m1.id,
    m1.timestamp,
    m1.body,
    m1.msg_box,
    m1.protocol,
    m1.user_id,
    m1.conversation_id,
    m1.created_at,
    COUNT(*) OVER (
        PARTITION BY m1.user_id, m1.conversation_id, m1.timestamp, m1.msg_box, m1.protocol, LOWER(TRIM(COALESCE(m1.body, '')))
    ) as duplicate_count
FROM messages m1
WHERE conversation_id IS NOT NULL
ORDER BY m1.timestamp DESC, m1.body;

-- Step 2: Delete duplicates, keeping only the oldest record (earliest created_at)
-- This ensures we keep the first import and remove subsequent duplicates
WITH duplicates AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY
                user_id,
                conversation_id,
                timestamp,
                msg_box,
                protocol,
                LOWER(TRIM(COALESCE(body, '')))
            ORDER BY created_at ASC  -- Keep the first one (oldest)
        ) as row_num
    FROM messages
    WHERE conversation_id IS NOT NULL
)
DELETE FROM messages
WHERE id IN (
    SELECT id FROM duplicates WHERE row_num > 1
);

-- Step 3: Verify cleanup (run AFTER Step 2)
-- This should show 0 duplicate_count if cleanup was successful
WITH potential_duplicates AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY
                user_id,
                conversation_id,
                timestamp,
                msg_box,
                protocol,
                LOWER(TRIM(COALESCE(body, '')))
            ORDER BY created_at ASC
        ) as row_num
    FROM messages
    WHERE conversation_id IS NOT NULL
)
SELECT
    COUNT(CASE WHEN row_num = 1 THEN 1 END) as unique_messages,
    COUNT(CASE WHEN row_num > 1 THEN 1 END) as remaining_duplicates,
    COUNT(*) as total_messages
FROM potential_duplicates;

