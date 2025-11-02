-- Consolidated baseline schema (merged former V1-V6)
-- Date: 2025-11-01
-- Includes:
--   * users, contacts, messages, message_parts (V1-V5)
--   * conversations & conversation_participants (V6)
--   * conversation-based duplicate detection indexes
--   * updated_at triggers via shared function
--   * bootstrap default user
-- ----------------------------------------------------------------------------------

-- 0. Users
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username      VARCHAR(40)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    DEFAULT now(),
    updated_at    TIMESTAMP    DEFAULT now()
);

-- 1. Contacts
CREATE TABLE contacts (
    id                BIGSERIAL PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    number            TEXT        NOT NULL,
    normalized_number TEXT        NOT NULL,
    name              TEXT,
    created_at        TIMESTAMP   DEFAULT now(),
    updated_at        TIMESTAMP   DEFAULT now()
);
CREATE UNIQUE INDEX ux_contacts_user_normalized ON contacts(user_id, normalized_number);
CREATE INDEX idx_contacts_user ON contacts(user_id);

-- 2. Conversations
CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(10) NOT NULL, -- SINGLE | GROUP
    external_thread_id TEXT,
    display_name TEXT,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX ix_conversations_user_type ON conversations(user_id, type);
CREATE UNIQUE INDEX ux_conversations_user_external ON conversations(user_id, external_thread_id) WHERE external_thread_id IS NOT NULL;

-- 3. Conversation participants (composite PK)
CREATE TABLE conversation_participants (
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    contact_id BIGINT NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    is_self BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (conversation_id, contact_id)
);
CREATE INDEX ix_conversation_participants_contact ON conversation_participants(contact_id);

-- 4. Messages (now conversation-scoped; legacy contact_id nullable)
CREATE TABLE messages (
    id           BIGSERIAL PRIMARY KEY,
    protocol     VARCHAR(10)  NOT NULL,               -- SMS|MMS|RCS
    direction    VARCHAR(10)  NOT NULL,               -- INBOUND|OUTBOUND
    sender       TEXT,
    recipient    TEXT,
    contact_id   BIGINT       REFERENCES contacts(id), -- nullable for GROUP convos
    conversation_id BIGINT    REFERENCES conversations(id) ON DELETE CASCADE, -- now nullable for legacy/backfill
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
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

-- 5. Message parts
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

-- 6. Indexes (messages)
CREATE INDEX idx_messages_timestamp    ON messages (timestamp);
CREATE INDEX idx_messages_contact      ON messages (contact_id);
CREATE INDEX idx_messages_sender       ON messages (sender);
CREATE INDEX idx_messages_recipient    ON messages (recipient);
CREATE INDEX idx_messages_direction    ON messages (direction);
CREATE INDEX idx_messages_protocol     ON messages (protocol);
CREATE INDEX idx_messages_user         ON messages (user_id);
CREATE INDEX idx_messages_conversation ON messages (conversation_id);
CREATE INDEX idx_messages_body_fts     ON messages USING gin (to_tsvector('english', coalesce(body,'')));

-- 7. Indexes (message_parts)
CREATE INDEX idx_message_parts_message ON message_parts (message_id);
CREATE INDEX idx_message_parts_ct      ON message_parts (ct);

-- 8. Audit trigger function (shared)
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 9. Triggers
CREATE TRIGGER trg_messages_updated_at      BEFORE UPDATE ON messages      FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_contacts_updated_at      BEFORE UPDATE ON contacts      FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_users_updated_at         BEFORE UPDATE ON users         FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_conversations_updated_at BEFORE UPDATE ON conversations FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 10. Enum-like checks (messages)
ALTER TABLE messages
    ADD CONSTRAINT chk_messages_protocol CHECK (protocol IN ('SMS','MMS','RCS')),
    ADD CONSTRAINT chk_messages_direction CHECK (direction IN ('INBOUND','OUTBOUND'));

-- 11. Conversation-based duplicate prevention
CREATE UNIQUE INDEX ux_messages_dedupe
    ON messages (conversation_id, timestamp, msg_box, protocol, md5(lower(coalesce(body,''))));
CREATE INDEX ix_messages_dedupe_prefix
    ON messages (conversation_id, timestamp, msg_box, protocol);

-- 12. Bootstrap default user
INSERT INTO users (id, username, password_hash) VALUES (
    '00000000-0000-0000-0000-000000000000',
    'default',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5s8dOa5Hf36fY4LEGy6Y6q5H0wE4.'
);

-- End consolidated baseline with conversations
