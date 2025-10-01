-- Ensure contact_name exists (in case older schema didn’t add it)
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS contact_name TEXT;