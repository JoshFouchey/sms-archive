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

-- Add unique constraint to prevent duplicate messages
-- This will skip duplicates during merge operations
-- Note: We use conversation_id instead of contact_id for better grouping
-- Constraint checks: same conversation, body, timestamp, direction, and user
-- PostgreSQL doesn't support IF NOT EXISTS for ADD CONSTRAINT, so we use DO block
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'unique_message_per_conversation'
    ) THEN
        ALTER TABLE messages ADD CONSTRAINT unique_message_per_conversation
            UNIQUE (conversation_id, body, timestamp, direction, user_id);
    END IF;
END $$;

-- Add comment explaining the constraint
COMMENT ON CONSTRAINT unique_message_per_conversation ON messages IS
    'Prevents duplicate messages in the same conversation. Used for merge deduplication.';

