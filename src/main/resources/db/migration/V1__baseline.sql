-- Consolidated baseline schema (originally V1-V5)
-- Includes users, contacts, conversations, messages, message_parts, all indexes, triggers, and user scoping.
-- This merged baseline replaces prior incremental migrations V2-V5.

-- 1. Users (authentication scope)
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username      VARCHAR(40)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    DEFAULT now(),
    updated_at    TIMESTAMP    DEFAULT now()
);

-- 2. Contacts (scoped per user)
CREATE TABLE contacts (
    id                BIGSERIAL PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    number            TEXT        NOT NULL,
    normalized_number TEXT        NOT NULL,
    name              TEXT,
    created_at        TIMESTAMP   DEFAULT now(),
    updated_at        TIMESTAMP   DEFAULT now()
);

-- 3. Conversations (group or 1:1 threads scoped per user)
CREATE TABLE conversations (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(12)  NOT NULL,            -- ONE_TO_ONE | GROUP
    name            TEXT,                             -- Group name or derived contact display
    last_message_at TIMESTAMP,                        -- Denormalization for fast listing
    created_at      TIMESTAMP    DEFAULT now(),
    updated_at      TIMESTAMP    DEFAULT now(),
    CONSTRAINT chk_conversations_type CHECK (type IN ('ONE_TO_ONE','GROUP'))
);

-- Join table linking conversations to contacts (participants)
CREATE TABLE conversation_contacts (
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    contact_id      BIGINT NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    PRIMARY KEY (conversation_id, contact_id)
);

-- 4. Messages (scoped per user, linked to contacts + optional conversation)
CREATE TABLE messages (
    id           BIGSERIAL PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    protocol     VARCHAR(10)  NOT NULL,              -- SMS|MMS|RCS
    direction    VARCHAR(10)  NOT NULL,              -- INBOUND|OUTBOUND
    sender       TEXT,
    recipient    TEXT,
    contact_id   BIGINT       NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    conversation_id BIGINT    REFERENCES conversations(id) ON DELETE SET NULL,
    timestamp    TIMESTAMP    NOT NULL,
    body         TEXT,
    msg_box      INTEGER,
    delivered_at TIMESTAMP,
    read_at      TIMESTAMP,
    media        JSONB,
    metadata     JSONB,
    created_at   TIMESTAMP    DEFAULT now(),
    updated_at   TIMESTAMP    DEFAULT now(),
    CONSTRAINT chk_messages_protocol  CHECK (protocol IN ('SMS','MMS','RCS')),
    CONSTRAINT chk_messages_direction CHECK (direction IN ('INBOUND','OUTBOUND'))
);

-- 5. Message parts (attachments/components)
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

-- 6. Updated_at trigger function (generic for all tables needing audit)
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 7. Triggers
CREATE TRIGGER trg_messages_updated_at BEFORE UPDATE ON messages FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_contacts_updated_at BEFORE UPDATE ON contacts FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_users_updated_at    BEFORE UPDATE ON users    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_conversations_updated_at BEFORE UPDATE ON conversations FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 8. Indexes (messages)
CREATE INDEX idx_messages_timestamp    ON messages ("timestamp");
CREATE INDEX idx_messages_contact      ON messages (contact_id);
CREATE INDEX idx_messages_sender       ON messages (sender);
CREATE INDEX idx_messages_recipient    ON messages (recipient);
CREATE INDEX idx_messages_direction    ON messages (direction);
CREATE INDEX idx_messages_protocol     ON messages (protocol);
CREATE INDEX idx_messages_user         ON messages (user_id);
CREATE INDEX idx_messages_conversation ON messages (conversation_id);
CREATE INDEX idx_messages_body_fts     ON messages USING gin (to_tsvector('english', coalesce(body,'')));

-- Duplicate prevention + probing
CREATE INDEX IF NOT EXISTS ix_messages_dedupe_prefix
    ON messages (user_id, contact_id, "timestamp", msg_box, protocol);

CREATE UNIQUE INDEX IF NOT EXISTS ux_messages_dedupe
    ON messages (user_id, contact_id, "timestamp", msg_box, protocol, md5(lower(coalesce(body,''))));

-- 9. Indexes (contacts)
CREATE UNIQUE INDEX ux_contacts_user_normalized ON contacts (user_id, normalized_number);
CREATE INDEX idx_contacts_user ON contacts (user_id);

-- 10. Indexes (message_parts)
CREATE INDEX idx_message_parts_message ON message_parts (message_id);
CREATE INDEX idx_message_parts_ct      ON message_parts (ct);

-- 11. Indexes (conversations & participants)
CREATE INDEX idx_conversations_user          ON conversations (user_id);
CREATE INDEX idx_conversations_last_message  ON conversations (last_message_at);
CREATE INDEX idx_conversation_contacts_conv  ON conversation_contacts (conversation_id);
CREATE INDEX idx_conversation_contacts_cont  ON conversation_contacts (contact_id);

-- 12. Bootstrap default user (password: 'password')
INSERT INTO users (id, username, password_hash)
VALUES ('00000000-0000-0000-0000-000000000000', 'default', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5s8dOa5Hf36fY4LEGy6Y6q5H0wE4.');

-- End of consolidated baseline including conversations
