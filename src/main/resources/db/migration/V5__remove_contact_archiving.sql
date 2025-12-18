-- Remove is_archived column from contacts table
-- We're simplifying the contact merge system by removing archiving
-- Contacts can still be merged (tracked via merged_into_id) but won't be archived

-- Drop the NOT NULL constraint first if it exists
ALTER TABLE contacts ALTER COLUMN is_archived DROP NOT NULL;

-- Then drop the column
ALTER TABLE contacts DROP COLUMN IF EXISTS is_archived;
