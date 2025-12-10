-- ============================================================================
-- PREVIEW DUPLICATE CONVERSATIONS - Read-only analysis script
-- ============================================================================
-- This script only SHOWS duplicate conversations without making any changes
-- Use this to see what would be merged before running the actual merge script
--
-- USAGE:
--   docker exec -i sms-archive-db-1 psql -U sms_user -d sms_archive < preview-duplicate-conversations.sql
-- ============================================================================

-- Find duplicate conversations (same participants)
WITH duplicate_conversation_groups AS (
    SELECT
        MIN(c.id) as primary_conversation_id,
        ARRAY_AGG(c.id ORDER BY c.id) as all_conversation_ids,
        c.user_id,
        STRING_AGG(cc.contact_id::TEXT, ',' ORDER BY cc.contact_id) as participant_signature,
        COUNT(DISTINCT c.id) as duplicate_count
    FROM conversations c
    JOIN conversation_contacts cc ON c.id = cc.conversation_id
    GROUP BY c.user_id, STRING_AGG(cc.contact_id::TEXT, ',' ORDER BY cc.contact_id)
    HAVING COUNT(DISTINCT c.id) > 1
)
SELECT
    dcg.primary_conversation_id as "Keep Conv ID",
    c.name as "Conversation Name",
    (SELECT COUNT(*) FROM messages WHERE conversation_id = dcg.primary_conversation_id) as "Msgs in Primary",
    dcg.duplicate_count as "Total Duplicates",
    dcg.all_conversation_ids as "All Duplicate IDs",
    (
        SELECT SUM(msg_count)
        FROM (
            SELECT COUNT(*) as msg_count
            FROM messages m
            WHERE m.conversation_id = ANY(dcg.all_conversation_ids)
              AND m.conversation_id != dcg.primary_conversation_id
        ) as subq
    ) as "Msgs in Dupes",
    (
        SELECT STRING_AGG(COALESCE(cont.name, cont.number), ', ' ORDER BY cont.name)
        FROM conversation_contacts cc2
        JOIN contacts cont ON cont.id = cc2.contact_id
        WHERE cc2.conversation_id = dcg.primary_conversation_id
    ) as "Participants"
FROM duplicate_conversation_groups dcg
JOIN conversations c ON c.id = dcg.primary_conversation_id
ORDER BY dcg.duplicate_count DESC, dcg.primary_conversation_id;

-- Summary
SELECT
    'Found ' || COALESCE(COUNT(*), 0) || ' groups of duplicate conversations' as "Summary"
FROM (
    SELECT
        MIN(c.id) as primary_conversation_id,
        COUNT(DISTINCT c.id) as duplicate_count
    FROM conversations c
    JOIN conversation_contacts cc ON c.id = cc.conversation_id
    GROUP BY c.user_id, STRING_AGG(cc.contact_id::TEXT, ',' ORDER BY cc.contact_id)
    HAVING COUNT(DISTINCT c.id) > 1
) as groups;

