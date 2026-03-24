-- Track which messages have been processed by KG extraction
-- Prevents re-processing on subsequent runs
CREATE TABLE kg_processed_messages (
    message_id  BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    processed_at TIMESTAMP DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);

CREATE INDEX idx_kg_processed_user ON kg_processed_messages (user_id);
