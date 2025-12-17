-- ============================================================================
-- PREVIEW DUPLICATE CONVERSATIONS - Safe read-only analysis
-- ============================================================================
-- This script identifies duplicate conversations WITHOUT making any changes
-- Use this to see what would be merged before running the actual merge script
--
-- USAGE:
--   psql -U sms_user -d sms_archive -f scripts/preview-duplicate-conversations.sql
--
-- Or from docker:
--   docker exec -i sms-archive-db-1 psql -U sms_user -d sms_archive < scripts/preview-duplicate-conversations.sql
-- ============================================================================

-- Find duplicate conversations (same participants)
WITH conversation_participants AS (
    SELECT
        c.id AS conversation_id,
        c.user_id,
        c.name,
        c.created_at,
        c.last_message_at,
        STRING_AGG(cc.contact_id::TEXT, ',' ORDER BY cc.contact_id) as participant_signature,
        STRING_AGG(
            COALESCE(cont.name, cont.number),
            ', ' ORDER BY cc.contact_id
        ) as participant_names
    FROM conversations c
    JOIN conversation_contacts cc ON c.id = cc.conversation_id
    JOIN contacts cont ON cont.id = cc.contact_id
    GROUP BY c.id, c.user_id, c.name, c.created_at, c.last_message_at
),
duplicate_groups AS (
    SELECT
        participant_signature,
        participant_names,
        user_id,
        ARRAY_AGG(conversation_id ORDER BY conversation_id) as conversation_ids,
        COUNT(*) as duplicate_count
    FROM conversation_participants
    GROUP BY user_id, participant_signature, participant_names
    HAVING COUNT(*) > 1
)
SELECT
    dg.conversation_ids[1] as "PRIMARY_ID (Keep)",
    array_to_string(dg.conversation_ids[2:array_length(dg.conversation_ids, 1)], ', ') as "DUPLICATE_IDS (Merge)",
    dg.duplicate_count as "Total",
    dg.participant_names as "Participants",
    (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = dg.conversation_ids[1]) as "Msgs_Primary",
    (SELECT STRING_AGG(
        conversation_id::TEXT || ':' || cnt::TEXT,
        ', ' ORDER BY conversation_id
    ) FROM (
        SELECT 
            conversation_id,
            COUNT(*) as cnt
        FROM messages m
        WHERE m.conversation_id = ANY(dg.conversation_ids[2:array_length(dg.conversation_ids, 1)])
        GROUP BY conversation_id
    ) sub) as "Msgs_Duplicates",
    (SELECT SUM(cnt) FROM (
        SELECT COUNT(*) as cnt
        FROM messages m
        WHERE m.conversation_id = ANY(dg.conversation_ids)
    ) x) as "Total_Msgs"
FROM duplicate_groups dg
ORDER BY dg.duplicate_count DESC, "Total_Msgs" DESC;

-- Summary statistics
SELECT
    COUNT(*) as "Groups of Duplicates",
    SUM(duplicate_count) as "Total Conversations",
    SUM(duplicate_count - 1) as "Conversations to Merge"
FROM (
    SELECT
        participant_signature,
        COUNT(*) as duplicate_count
    FROM (
        SELECT
            c.id AS conversation_id,
            c.user_id,
            STRING_AGG(cc.contact_id::TEXT, ',' ORDER BY cc.contact_id) as participant_signature
        FROM conversations c
        JOIN conversation_contacts cc ON c.id = cc.conversation_id
        GROUP BY c.id, c.user_id
    ) cp
    GROUP BY user_id, participant_signature
    HAVING COUNT(*) > 1
) dg;
