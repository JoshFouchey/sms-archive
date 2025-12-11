BEGIN;

-- Step 0: Build participant signature per conversation
WITH conversation_participants AS (
    SELECT
        c.id,
        c.user_id,
        STRING_AGG(cc.contact_id::TEXT, ',' ORDER BY cc.contact_id) AS participant_signature
    FROM conversations c
    JOIN conversation_contacts cc ON c.id = cc.conversation_id
    GROUP BY c.id, c.user_id
),

-- Step 1: Identify duplicate groups
duplicate_conversation_groups AS (
    SELECT
        MIN(id) AS primary_conversation_id,
        ARRAY_AGG(id ORDER BY id) AS all_conversation_ids,
        user_id,
        participant_signature,
        COUNT(*) AS duplicate_count
    FROM conversation_participants
    GROUP BY user_id, participant_signature
    HAVING COUNT(*) > 1
)

-- Create the temp table
CREATE TEMP TABLE duplicate_conversation_groups AS
SELECT * FROM duplicate_conversation_groups;

-- Show what was found
SELECT
    'Found ' || COUNT(*) || ' groups of duplicate conversations affecting ' ||
    SUM(duplicate_count - 1) || ' conversations to be merged' AS summary
FROM duplicate_conversation_groups;

-- Details
SELECT
    dcg.primary_conversation_id AS "Keep (Primary)",
    dcg.all_conversation_ids AS "Duplicate IDs",
    dcg.duplicate_count AS "Total Dupes",
    c.name AS "Conversation Name",
    COUNT(m.id) AS "Messages in Primary"
FROM duplicate_conversation_groups dcg
JOIN conversations c ON c.id = dcg.primary_conversation_id
LEFT JOIN messages m ON m.conversation_id = c.id
GROUP BY dcg.primary_conversation_id, dcg.all_conversation_ids,
         dcg.duplicate_count, c.name
ORDER BY dcg.duplicate_count DESC;

-- Step 2: Move messages
UPDATE messages m
SET conversation_id = dcg.primary_conversation_id
FROM duplicate_conversation_groups dcg
WHERE m.conversation_id = ANY(dcg.all_conversation_ids)
  AND m.conversation_id != dcg.primary_conversation_id;

-- How many moved
SELECT
    'Moved ' || COUNT(*) || ' messages to primary conversations' AS summary
FROM messages m
JOIN duplicate_conversation_groups dcg
  ON m.conversation_id = dcg.primary_conversation_id;

-- Step 3: Update last_message_at
UPDATE conversations c
SET last_message_at = (
    SELECT MAX(m.timestamp)
    FROM messages m
    WHERE m.conversation_id = c.id
)
WHERE c.id IN (SELECT primary_conversation_id FROM duplicate_conversation_groups);

-- Step 4: Delete duplicate conversation_contacts
DELETE FROM conversation_contacts cc
USING duplicate_conversation_groups dcg
WHERE cc.conversation_id = ANY(dcg.all_conversation_ids)
  AND cc.conversation_id != dcg.primary_conversation_id;

-- Step 5: Delete duplicate conversations
DELETE FROM conversations c
USING duplicate_conversation_groups dcg
WHERE c.id = ANY(dcg.all_conversation_ids)
  AND c.id != dcg.primary_conversation_id;

-- Final summary
SELECT
    'Cleanup complete! Merged ' || COUNT(*) || ' groups of duplicates' AS summary
FROM duplicate_conversation_groups;

-- Remaining conversations per user
SELECT
    u.username,
    COUNT(DISTINCT c.id) AS conversation_count,
    SUM((SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id)) AS total_messages
FROM users u
LEFT JOIN conversations c ON c.user_id = u.id
GROUP BY u.username;

COMMIT;
