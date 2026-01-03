Here-- ==========================================
-- SAFE Contact Normalization Migration
-- Handles duplicates by merging before updating
-- ==========================================

\echo '=== Step 1: Find contacts that will become duplicates ==='
\echo ''

-- Find what duplicates will be created
SELECT 
    c1.id as existing_with_plus_id,
    c1.normalized_number as existing_normalized,
    c1.name as existing_name,
    c1.created_at as existing_created,
    c2.id as needs_plus_id,
    c2.normalized_number as needs_plus_normalized,
    c2.name as needs_plus_name,
    c2.created_at as needs_plus_created,
    (SELECT COUNT(*) FROM messages WHERE sender_contact_id = c1.id) as existing_message_count,
    (SELECT COUNT(*) FROM messages WHERE sender_contact_id = c2.id) as needs_plus_message_count
FROM contacts c1
JOIN contacts c2 ON c1.user_id = c2.user_id
WHERE c1.normalized_number = '+' || c2.normalized_number
  AND c1.normalized_number LIKE '+%'
  AND c2.normalized_number NOT LIKE '+%'
  AND c2.normalized_number != '__unknown__'
ORDER BY c1.id;

\echo ''
\echo '=== Step 2: Merge duplicates (keeping older contact) ==='
\echo 'This will:'
\echo '  - Keep the contact WITH + prefix (older)'
\echo '  - Update all messages/conversations to point to it'
\echo '  - Delete the contact WITHOUT + prefix'
\echo ''

-- Update messages to point to the contact WITH + prefix
UPDATE messages m
SET sender_contact_id = (
    SELECT c1.id
    FROM contacts c1
    WHERE c1.user_id = (SELECT user_id FROM contacts WHERE id = m.sender_contact_id)
      AND c1.normalized_number = '+' || (
          SELECT normalized_number 
          FROM contacts c2 
          WHERE c2.id = m.sender_contact_id
      )
      AND c1.normalized_number LIKE '+%'
)
WHERE sender_contact_id IN (
    SELECT c2.id
    FROM contacts c1
    JOIN contacts c2 ON c1.user_id = c2.user_id
    WHERE c1.normalized_number = '+' || c2.normalized_number
      AND c1.normalized_number LIKE '+%'
      AND c2.normalized_number NOT LIKE '+%'
      AND c2.normalized_number != '__unknown__'
);

\echo 'Messages updated to point to contacts with + prefix'
\echo ''

-- Update conversation_contacts to point to the contact WITH + prefix
UPDATE conversation_contacts cc
SET contact_id = (
    SELECT c1.id
    FROM contacts c1
    WHERE c1.user_id = (SELECT user_id FROM contacts WHERE id = cc.contact_id)
      AND c1.normalized_number = '+' || (
          SELECT normalized_number 
          FROM contacts c2 
          WHERE c2.id = cc.contact_id
      )
      AND c1.normalized_number LIKE '+%'
)
WHERE contact_id IN (
    SELECT c2.id
    FROM contacts c1
    JOIN contacts c2 ON c1.user_id = c2.user_id
    WHERE c1.normalized_number = '+' || c2.normalized_number
      AND c1.normalized_number LIKE '+%'
      AND c2.normalized_number NOT LIKE '+%'
      AND c2.normalized_number != '__unknown__'
);

\echo 'Conversation participants updated to point to contacts with + prefix'
\echo ''

-- Delete duplicate contacts WITHOUT + prefix
DELETE FROM contacts
WHERE id IN (
    SELECT c2.id
    FROM contacts c1
    JOIN contacts c2 ON c1.user_id = c2.user_id
    WHERE c1.normalized_number = '+' || c2.normalized_number
      AND c1.normalized_number LIKE '+%'
      AND c2.normalized_number NOT LIKE '+%'
      AND c2.normalized_number != '__unknown__'
);

\echo 'Deleted duplicate contacts without + prefix'
\echo ''

-- Step 3: Now add + prefix to remaining contacts that don't have it
\echo '=== Step 3: Add + prefix to remaining contacts ==='

UPDATE contacts
SET normalized_number = '+' || normalized_number,
    updated_at = NOW()
WHERE normalized_number NOT LIKE '+%' 
AND normalized_number != '__unknown__';

\echo 'Added + prefix to remaining contacts'
\echo ''

-- Step 4: Verify
\echo '=== Step 4: Verification ==='
SELECT 
    COUNT(*) FILTER (WHERE normalized_number LIKE '+%') as with_plus,
    COUNT(*) FILTER (WHERE normalized_number NOT LIKE '+%' AND normalized_number != '__unknown__') as without_plus,
    COUNT(*) FILTER (WHERE normalized_number = '__unknown__') as unknown,
    COUNT(*) as total
FROM contacts;

\echo ''
\echo 'Expected: without_plus = 0'
\echo ''

-- Check for any remaining duplicates
\echo '=== Checking for remaining duplicates ==='
SELECT 
    user_id,
    normalized_number,
    COUNT(*) as count
FROM contacts
GROUP BY user_id, normalized_number
HAVING COUNT(*) > 1;

\echo ''
\echo 'Expected: 0 rows (no duplicates)'
\echo ''
\echo 'Done! All contacts standardized to + prefix format.'

