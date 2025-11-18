-- V3: Enforce lowercase usernames
-- Add check constraint to ensure all usernames are stored in lowercase

-- First, normalize any existing usernames to lowercase (should be no-op if already lowercase)
UPDATE users SET username = LOWER(username) WHERE username <> LOWER(username);

-- Add constraint to enforce lowercase at database level
ALTER TABLE users ADD CONSTRAINT chk_username_lowercase
CHECK (username = LOWER(username));

COMMENT ON CONSTRAINT chk_username_lowercase ON users IS
    'Ensures usernames are always stored in lowercase for case-insensitive comparison';

