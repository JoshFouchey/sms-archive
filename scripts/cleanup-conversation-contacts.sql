-- ============================================================================
-- CLEANUP CONVERSATION_CONTACTS AFTER CONTACT MERGES
-- ============================================================================
-- This script removes references to deleted/archived contacts in conversation_contacts
-- and identifies conversations that should be merged after cleanup
--
-- USAGE:
--   Run: docker exec -i sms-archive-db psql -U sms_user -d sms_archive < scripts/cleanup-conversation-contacts.sql
-- ============================================================================

BEGIN;

-- Find orphaned conversation_contacts (pointing to non-existent contacts)
\echo '=== ORPHANED CONVERSATION_CONTACTS (pointing to deleted contacts) ==='
SELECT 
    cc.conversation_id,
    cc.contact_id as orphaned_contact_id,
    c.name as conversation_name,
    'Contact ID ' || cc.contact_id || ' does not exist' as issue
FROM conversation_contacts cc
JOIN conversations c ON c.id = cc.conversation_id
LEFT JOIN contacts cont ON cont.id = cc.contact_id
WHERE cont.id IS NULL
ORDER BY cc.conversation_id;

-- Count how many orphaned records
SELECT 
    COUNT(*) as orphaned_count,
    'Total orphaned conversation_contacts records' as description
FROM conversation_contacts cc
LEFT JOIN contacts cont ON cont.id = cc.contact_id
WHERE cont.id IS NULL;

-- Delete orphaned conversation_contacts
DELETE FROM conversation_contacts cc
USING (
    SELECT cc2.conversation_id, cc2.contact_id
    FROM conversation_contacts cc2
    LEFT JOIN contacts cont ON cont.id = cc2.contact_id
    WHERE cont.id IS NULL
) orphaned
WHERE cc.conversation_id = orphaned.conversation_id
  AND cc.contact_id = orphaned.contact_id;

\echo '=== CLEANUP COMPLETE ==='
SELECT 
    'Deleted orphaned conversation_contacts records' as action,
    COUNT(*) as deleted_count
FROM conversation_contacts cc
LEFT JOIN contacts cont ON cont.id = cc.contact_id
WHERE cont.id IS NULL;

-- Now check for duplicate conversations again
\echo '=== DUPLICATE CONVERSATIONS AFTER CLEANUP ==='
WITH conversation_participants AS (
    SELECT
        c.id,
        c.user_id,
        STRING_AGG(cc.contact_id::TEXT, ',' ORDER BY cc.contact_id) AS participant_signature
    FROM conversations c
    JOIN conversation_contacts cc ON c.id = cc.conversation_id
    GROUP BY c.id, c.user_id
),
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
SELECT
    dcg.primary_conversation_id AS "Keep Conv ID",
    c.name AS "Conversation Name",
    (SELECT COUNT(*) FROM messages WHERE conversation_id = dcg.primary_conversation_id) AS "Msgs in Primary",
    dcg.duplicate_count AS "Total Duplicates",
    dcg.all_conversation_ids AS "All Duplicate IDs",
    (SELECT STRING_AGG(COALESCE(cont.name, cont.number), ', ' ORDER BY cont.name)
     FROM conversation_contacts cc2
     JOIN contacts cont ON cont.id = cc2.contact_id
     WHERE cc2.conversation_id = dcg.primary_conversation_id) AS "Participants"
FROM duplicate_conversation_groups dcg
JOIN conversations c ON c.id = dcg.primary_conversation_id
ORDER BY dcg.duplicate_count DESC, dcg.primary_conversation_id;

-- Show summary
SELECT 
    COUNT(*) as duplicate_groups,
    'New duplicate conversation groups found after cleanup' as description
FROM (
    SELECT
        STRING_AGG(cc.contact_id::TEXT, ',' ORDER BY cc.contact_id) AS participant_signature,
        user_id
    FROM conversations c
    JOIN conversation_contacts cc ON c.id = cc.conversation_id
    GROUP BY c.id, c.user_id
) sigs
GROUP BY user_id, participant_signature
HAVING COUNT(*) > 1;

COMMIT;

-- ============================================================================
-- After running this:
--   1. If duplicates are found, run merge-duplicate-conversations.sql again
--   2. This should catch the conversations that are now identical after cleanup
-- ============================================================================
