-- V1__init.sql

-- SMS table
CREATE TABLE sms (
                     id SERIAL PRIMARY KEY,
                     protocol SMALLINT,
                     address TEXT,
                     date TIMESTAMP,
                     msg_box SMALLINT,
                     body TEXT,
                     contact_name TEXT,
                     raw_json JSONB
);

-- MMS table
CREATE TABLE mms (
                     id SERIAL PRIMARY KEY,
                     date TIMESTAMP,
                     msg_box SMALLINT,
                     address TEXT,
                     contact_name TEXT,
                     raw_json JSONB
);

-- MMS parts (attachments, text, images, etc.)
CREATE TABLE mms_parts (
                           id SERIAL PRIMARY KEY,
                           mms_id INT REFERENCES mms(id) ON DELETE CASCADE,
                           seq INT,
                           ct TEXT,          -- content type (e.g., text/plain, image/jpeg)
                           name TEXT,
                           text TEXT,
                           file_path TEXT
);

-- MMS addresses (to/from)
CREATE TABLE mms_addrs (
                           id SERIAL PRIMARY KEY,
                           mms_id INT REFERENCES mms(id) ON DELETE CASCADE,
                           address TEXT,
                           type SMALLINT
);

-- ========================
-- Indexes for performance
-- ========================

-- SMS
CREATE INDEX idx_sms_date ON sms(date);
CREATE INDEX idx_sms_address ON sms(address);

-- MMS
CREATE INDEX idx_mms_date ON mms(date);
CREATE INDEX idx_mms_address ON mms(address);

-- MMS parts (search by content type or text)
CREATE INDEX idx_mms_parts_ct ON mms_parts(ct);
CREATE INDEX idx_mms_parts_text_gin ON mms_parts USING gin (to_tsvector('english', text));

-- MMS addrs
CREATE INDEX idx_mms_addrs_address ON mms_addrs(address);
