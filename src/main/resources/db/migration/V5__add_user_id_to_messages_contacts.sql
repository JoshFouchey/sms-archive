-- V5: Add user scoping to contacts and messages
-- Drop existing unique index on normalized_number (will recreate composite)
DROP INDEX IF EXISTS ux_contacts_normalized;

ALTER TABLE contacts ADD COLUMN user_id UUID;
ALTER TABLE messages ADD COLUMN user_id UUID;

-- Populate existing rows with default user id
UPDATE contacts SET user_id = '00000000-0000-0000-0000-000000000000' WHERE user_id IS NULL;
UPDATE messages SET user_id = '00000000-0000-0000-0000-000000000000' WHERE user_id IS NULL;

-- Add constraints and not-null
ALTER TABLE contacts ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE messages ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE contacts ADD CONSTRAINT fk_contacts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE messages ADD CONSTRAINT fk_messages_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- New composite unique index per user for normalized number
CREATE UNIQUE INDEX ux_contacts_user_normalized ON contacts(user_id, normalized_number);

-- Indexes for user filtering
CREATE INDEX idx_messages_user ON messages(user_id);
CREATE INDEX idx_contacts_user ON contacts(user_id);

