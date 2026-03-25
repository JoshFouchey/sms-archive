-- V15: Add contextual embedding support
-- Stores the synthetic search document used to generate the embedding
-- so we can inspect/debug what was actually embedded for each message.

ALTER TABLE message_embeddings ADD COLUMN embedding_text TEXT;

-- For semantic chunking (future): long messages split into multiple chunks
ALTER TABLE message_embeddings ADD COLUMN parent_message_id BIGINT REFERENCES messages(id);
ALTER TABLE message_embeddings ADD COLUMN chunk_index INT DEFAULT 0;

-- Drop the unique constraint that assumes 1 embedding per message per model
-- (chunks need multiple rows per message)
ALTER TABLE message_embeddings DROP CONSTRAINT IF EXISTS uq_message_embedding;

-- New unique constraint: message + model + chunk
ALTER TABLE message_embeddings
    ADD CONSTRAINT uq_message_embedding_chunk UNIQUE (message_id, model_name, chunk_index);
