-- ============================================================================
-- DEBUG DUPLICATE CONVERSATIONS
-- ============================================================================
-- Use this to understand why two conversations aren't being detected as duplicates
--
-- USAGE:
--   1. Update the conversation IDs below (lines 12-13)
--   2. Run: docker exec -i sms-archive-db psql -U sms_user -d sms_archive < scripts/debug-duplicate-conversations.sql
-- ============================================================================

-- ===== CONFIGURATION - UPDATE THESE VALUES =====
DO $$ BEGIN RAISE NOTICE 'Conversation ID 1: 123'; END $$;
DO $$ BEGIN RAISE NOTICE 'Conversation ID 2: 456'; END $$;

\echo '=== CONVERSATION DETAILS ==='
SELECT 
    c.id as conversation_id,
    c.name,
    c.user_id,
    (SELECT COUNT(*) FROM messages WHERE conversation_id = c.id) as message_count,
    c.created_at,
    c.last_message_at
FROM conversations c
WHERE c.id IN (123, 456)  -- CHANGE THESE
ORDER BY c.id;

\echo '=== PARTICIPANTS ==='
SELECT 
    cc.conversation_id,
    cc.contact_id,
    cont.name as contact_name,
    cont.number as contact_number,
    cont.normalized_number
FROM conversation_contacts cc
JOIN contacts cont ON cont.id = cc.contact_id
WHERE cc.conversation_id IN (123, 456)  -- CHANGE THESE
ORDER BY cc.conversation_id, cc.contact_id;

\echo '=== PARTICIPANT SIGNATURES (used for duplicate detection) ==='
WITH conversation_participants AS (
    SELECT
        c.id AS conversation_id,
        c.name,
        c.user_id,
        STRING_AGG(cc.contact_id::TEXT, ',' ORDER BY cc.contact_id) as participant_signature
    FROM conversations c
    JOIN conversation_contacts cc ON c.id = cc.conversation_id
    WHERE c.id IN (123, 456)  -- CHANGE THESE
    GROUP BY c.id, c.name, c.user_id
)
SELECT 
    conversation_id,
    name,
    user_id,
    participant_signature,
    CASE 
        WHEN participant_signature = (
            SELECT participant_signature 
            FROM conversation_participants 
            WHERE conversation_id != cp.conversation_id
            LIMIT 1
        ) THEN 'MATCH - Should be detected as duplicate'
        ELSE 'NO MATCH - Different participants'
    END as duplicate_status
FROM conversation_participants cp
ORDER BY conversation_id;

\echo '=== PARTICIPANT COMPARISON ==='
SELECT 
    'Contacts only in conversation 123' as detail,  -- CHANGE THIS
    STRING_AGG(cont.name || ' (ID: ' || cont.id || ')', ', ') as contacts
FROM conversation_contacts cc
JOIN contacts cont ON cont.id = cc.contact_id
WHERE cc.conversation_id = 123  -- CHANGE THIS
  AND cc.contact_id NOT IN (
      SELECT contact_id 
      FROM conversation_contacts
      WHERE conversation_id = 456  -- CHANGE THIS
  )

UNION ALL

SELECT 
    'Contacts only in conversation 456' as detail,  -- CHANGE THIS
    STRING_AGG(cont.name || ' (ID: ' || cont.id || ')', ', ') as contacts
FROM conversation_contacts cc
JOIN contacts cont ON cont.id = cc.contact_id
WHERE cc.conversation_id = 456  -- CHANGE THIS
  AND cc.contact_id NOT IN (
      SELECT contact_id 
      FROM conversation_contacts
      WHERE conversation_id = 123  -- CHANGE THIS
  )

UNION ALL

SELECT 
    'Contacts in BOTH conversations' as detail,
    STRING_AGG(cont.name || ' (ID: ' || cont.id || ')', ', ') as contacts
FROM conversation_contacts cc
JOIN contacts cont ON cont.id = cc.contact_id
WHERE cc.conversation_id = 123  -- CHANGE THIS
  AND cc.contact_id IN (
      SELECT contact_id 
      FROM conversation_contacts
      WHERE conversation_id = 456  -- CHANGE THIS
  );

\echo '=== RECENT MESSAGES (last 5 per conversation) ==='
(
    SELECT 
        123 as conversation_id,  -- CHANGE THIS
        m.id as message_id,
        m.direction,
        LEFT(m.body, 50) as body_preview,
        m.timestamp
    FROM messages m
    WHERE m.conversation_id = 123  -- CHANGE THIS
    ORDER BY m.timestamp DESC
    LIMIT 5
)
UNION ALL
(
    SELECT 
        456 as conversation_id,  -- CHANGE THIS
        m.id as message_id,
        m.direction,
        LEFT(m.body, 50) as body_preview,
        m.timestamp
    FROM messages m
    WHERE m.conversation_id = 456  -- CHANGE THIS
    ORDER BY m.timestamp DESC
    LIMIT 5
)
ORDER BY conversation_id, timestamp DESC;
