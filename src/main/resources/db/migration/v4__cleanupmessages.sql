-- V3__cleanup_messages.sql

-- Drop obsolete columns if they exist
ALTER TABLE messages
DROP COLUMN IF EXISTS address,
    DROP COLUMN IF EXISTS date,
    DROP COLUMN IF EXISTS msg_box,
    DROP COLUMN IF EXISTS raw_json;

-- Drop any leftover join table style columns from old import
ALTER TABLE messages
DROP COLUMN IF EXISTS ct,
    DROP COLUMN IF EXISTS file_path,
    DROP COLUMN IF EXISTS name,
    DROP COLUMN IF EXISTS seq,
    DROP COLUMN IF EXISTS text,
    DROP COLUMN IF EXISTS message_id;

-- Optional: tighten constraints
ALTER TABLE messages
    ALTER COLUMN protocol SET NOT NULL,
ALTER COLUMN timestamp SET NOT NULL;
