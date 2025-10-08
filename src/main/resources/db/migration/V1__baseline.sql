-- Baseline schema with contacts normalization and full message/part fields

-- 1. Contacts
CREATE TABLE contacts (
                          id                BIGSERIAL PRIMARY KEY,
                          number            TEXT        NOT NULL,
                          normalized_number TEXT        NOT NULL,
                          name              TEXT,
                          created_at        TIMESTAMP   DEFAULT now(),
                          updated_at        TIMESTAMP   DEFAULT now()
);
CREATE UNIQUE INDEX ux_contacts_normalized ON contacts (normalized_number);

-- 2. Messages
CREATE TABLE messages (
                          id           BIGSERIAL PRIMARY KEY,
                          protocol     VARCHAR(10)  NOT NULL,              -- SMS|MMS|RCS
                          direction    VARCHAR(10)  NOT NULL,              -- INBOUND|OUTBOUND
                          sender       TEXT,
                          recipient    TEXT,
                          contact_id   BIGINT       NOT NULL REFERENCES contacts(id),
                          timestamp    TIMESTAMP    NOT NULL,
                          body         TEXT,
                          msg_box      INTEGER,
                          delivered_at TIMESTAMP,
                          read_at      TIMESTAMP,
                          media        JSONB,
                          metadata     JSONB,
                          created_at   TIMESTAMP    DEFAULT now(),
                          updated_at   TIMESTAMP    DEFAULT now()
);

-- 3. Message parts (MMS/RCS components, attachments)
CREATE TABLE message_parts (
                               id          BIGSERIAL PRIMARY KEY,
                               message_id  BIGINT      NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
                               seq         INTEGER,
                               ct          VARCHAR(100),
                               name        TEXT,
                               text        TEXT,
                               file_path   TEXT,
                               size_bytes  BIGINT
);

-- 4. Indexes (messages)
CREATE INDEX idx_messages_timestamp  ON messages (timestamp);
CREATE INDEX idx_messages_contact    ON messages (contact_id);
CREATE INDEX idx_messages_sender     ON messages (sender);
CREATE INDEX idx_messages_recipient  ON messages (recipient);
CREATE INDEX idx_messages_direction  ON messages (direction);
CREATE INDEX idx_messages_protocol   ON messages (protocol);

-- Full-text (optional)
CREATE INDEX idx_messages_body_fts ON messages USING gin (to_tsvector('english', coalesce(body,'')));

-- 5. Indexes (message_parts)
CREATE INDEX idx_message_parts_message ON message_parts (message_id);
CREATE INDEX idx_message_parts_ct      ON message_parts (ct);

-- 6. Audit triggers for updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_messages_updated_at
    BEFORE UPDATE ON messages
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_contacts_updated_at
    BEFORE UPDATE ON contacts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 7. Enum-like checks
ALTER TABLE messages
    ADD CONSTRAINT chk_messages_protocol CHECK (protocol IN ('SMS','MMS','RCS')),
    ADD CONSTRAINT chk_messages_direction CHECK (direction IN ('INBOUND','OUTBOUND'));
