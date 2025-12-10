-- Add support for contact merging and duplicate message detection

-- Add archive and merge tracking to contacts table
ALTER TABLE contacts ADD COLUMN IF NOT EXISTS is_archived BOOLEAN DEFAULT FALSE;
ALTER TABLE contacts ADD COLUMN IF NOT EXISTS merged_into_id BIGINT REFERENCES contacts(id);
ALTER TABLE contacts ADD COLUMN IF NOT EXISTS merged_at TIMESTAMP;

-- Update existing contacts to have is_archived = FALSE where it's null
UPDATE contacts SET is_archived = FALSE WHERE is_archived IS NULL;

-- Make is_archived NOT NULL after setting defaults
ALTER TABLE contacts ALTER COLUMN is_archived SET NOT NULL;

-- Create index for archived contacts to speed up queries
CREATE INDEX IF NOT EXISTS idx_contacts_archived ON contacts(is_archived) WHERE is_archived = FALSE;

-- Create index for merged contacts lookup
CREATE INDEX IF NOT EXISTS idx_contacts_merged_into ON contacts(merged_into_id) WHERE merged_into_id IS NOT NULL;

-- Remove existing duplicate messages before adding unique constraint
-- Keep the oldest message (lowest id) in each duplicate group
DELETE FROM messages
WHERE id IN (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY conversation_id, body, timestamp, direction, user_id
                   ORDER BY id ASC
               ) AS rn
        FROM messages
    ) AS duplicates
    WHERE rn > 1
);

-- Add unique index to prevent duplicate messages using MD5 hash of body
-- This prevents btree index size limits (body can be very large)
-- The index checks: same conversation, body hash, timestamp, direction, and user
CREATE UNIQUE INDEX IF NOT EXISTS unique_message_per_conversation
    ON messages (conversation_id, MD5(COALESCE(body, '')), timestamp, direction, user_id);

