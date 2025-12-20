-- Merge duplicate contacts that differ only by + prefix
-- Keeps the contact WITH + prefix, merges the one without

BEGIN;

-- Preview what will be merged (run this first to review)
SELECT 
    c1.id as keep_id,
    c1.number as keep_number,
    c1.normalized_number,
    c2.id as merge_id,
    c2.number as merge_number,
    COUNT(DISTINCT cc.conversation_id) as conversations_affected,
    COUNT(DISTINCT m.id) as messages_affected
FROM contacts c1
JOIN contacts c2 ON 
    c1.user_id = c2.user_id 
    AND c1.normalized_number = c2.normalized_number
    AND c1.id < c2.id  -- Ensure we only get pairs once
LEFT JOIN conversation_contacts cc ON cc.contact_id = c2.id
LEFT JOIN messages m ON m.sender_contact_id = c2.id
WHERE c1.number LIKE '+%'  -- Keep the one with +
  AND c2.number NOT LIKE '+%'  -- Merge the one without +
GROUP BY c1.id, c1.number, c1.normalized_number, c2.id, c2.number
ORDER BY conversations_affected DESC, messages_affected DESC;

-- Uncomment below to execute the merge (after reviewing above results)
/*

-- Step 1: Update conversation_contacts to point to the contact with +
UPDATE conversation_contacts cc
SET contact_id = (
    SELECT c1.id 
    FROM contacts c1
    JOIN contacts c2 ON 
        c1.user_id = c2.user_id 
        AND c1.normalized_number = c2.normalized_number
        AND c1.id < c2.id
    WHERE c1.number LIKE '+%'
      AND c2.number NOT LIKE '+%'
      AND c2.id = cc.contact_id
    LIMIT 1
)
WHERE EXISTS (
    SELECT 1
    FROM contacts c1
    JOIN contacts c2 ON 
        c1.user_id = c2.user_id 
        AND c1.normalized_number = c2.normalized_number
        AND c1.id < c2.id
    WHERE c1.number LIKE '+%'
      AND c2.number NOT LIKE '+%'
      AND c2.id = cc.contact_id
);

-- Step 2: Update messages.sender_contact_id to point to the contact with +
UPDATE messages m
SET sender_contact_id = (
    SELECT c1.id 
    FROM contacts c1
    JOIN contacts c2 ON 
        c1.user_id = c2.user_id 
        AND c1.normalized_number = c2.normalized_number
        AND c1.id < c2.id
    WHERE c1.number LIKE '+%'
      AND c2.number NOT LIKE '+%'
      AND c2.id = m.sender_contact_id
    LIMIT 1
)
WHERE EXISTS (
    SELECT 1
    FROM contacts c1
    JOIN contacts c2 ON 
        c1.user_id = c2.user_id 
        AND c1.normalized_number = c2.normalized_number
        AND c1.id < c2.id
    WHERE c1.number LIKE '+%'
      AND c2.number NOT LIKE '+%'
      AND c2.id = m.sender_contact_id
);

-- Step 3: Delete the duplicate contacts (without +)
DELETE FROM contacts c2
WHERE EXISTS (
    SELECT 1
    FROM contacts c1
    WHERE c1.user_id = c2.user_id 
      AND c1.normalized_number = c2.normalized_number
      AND c1.id < c2.id
      AND c1.number LIKE '+%'
      AND c2.number NOT LIKE '+%'
);

-- Show summary of merge
SELECT 
    COUNT(*) as total_contacts,
    COUNT(CASE WHEN number LIKE '+%' THEN 1 END) as contacts_with_plus,
    COUNT(CASE WHEN number NOT LIKE '+%' AND number != '__unknown__' THEN 1 END) as contacts_without_plus
FROM contacts;

*/

ROLLBACK;  -- Change to COMMIT when ready to execute
