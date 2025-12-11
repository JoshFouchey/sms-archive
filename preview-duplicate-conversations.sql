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
    (
        SELECT SUM(cnt)
        FROM (
            SELECT COUNT(*) AS cnt
            FROM messages m
            WHERE m.conversation_id = ANY(dcg.all_conversation_ids)
              AND m.conversation_id != dcg.primary_conversation_id
        ) AS subq
    ) AS "Msgs in Dupes",
    (
        SELECT STRING_AGG(COALESCE(cont.name, cont.number), ', ' ORDER BY cont.name)
        FROM conversation_contacts cc2
        JOIN contacts cont ON cont.id = cc2.contact_id
        WHERE cc2.conversation_id = dcg.primary_conversation_id
    ) AS "Participants"
FROM duplicate_conversation_groups dcg
JOIN conversations c ON c.id = dcg.primary_conversation_id
ORDER BY dcg.duplicate_count DESC, dcg.primary_conversation_id;
