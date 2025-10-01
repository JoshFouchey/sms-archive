-- Drop old tables (CAUTION: destructive!)
DROP TABLE IF EXISTS mms_parts CASCADE;
DROP TABLE IF EXISTS mms_addrs CASCADE;
DROP TABLE IF EXISTS mms CASCADE;
DROP TABLE IF EXISTS sms CASCADE;

-- Create unified messages table
CREATE TABLE messages (
                          id BIGSERIAL PRIMARY KEY,
                          protocol VARCHAR(10) NOT NULL, -- 'sms' | 'mms' | 'rcs'
                          sender TEXT,
                          recipient TEXT,
                          timestamp TIMESTAMP NOT NULL,
                          body TEXT,
                          media JSONB,          -- stores MMS parts, RCS attachments, gifs, etc
                          metadata JSONB        -- delivery status, reactions, read receipts, etc
);

-- Indexes
CREATE INDEX idx_messages_timestamp ON messages (timestamp);
CREATE INDEX idx_messages_sender ON messages (sender);
CREATE INDEX idx_messages_recipient ON messages (recipient);
CREATE INDEX idx_messages_body_gin ON messages USING gin (to_tsvector('english', body));
