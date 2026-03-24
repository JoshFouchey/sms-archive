-- Enable pgvector extension for semantic search embeddings
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================================
-- Message Embeddings
-- Stores vector embeddings for semantic (RAG) search over messages.
-- Separate table to keep the messages table clean and allow re-embedding.
-- ============================================================================
CREATE TABLE message_embeddings (
    id              BIGSERIAL PRIMARY KEY,
    message_id      BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    embedding       vector(768),
    model_name      VARCHAR(100) NOT NULL DEFAULT 'nomic-embed-text',
    created_at      TIMESTAMP DEFAULT now(),
    CONSTRAINT uq_message_embedding UNIQUE (message_id, model_name)
);

-- HNSW index for fast approximate nearest neighbor search (cosine distance)
CREATE INDEX idx_message_embeddings_vector
    ON message_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 128);

CREATE INDEX idx_message_embeddings_user ON message_embeddings (user_id);
CREATE INDEX idx_message_embeddings_message ON message_embeddings (message_id);

-- ============================================================================
-- Embedding Jobs
-- Tracks batch embedding progress (same pattern as import jobs).
-- ============================================================================
CREATE TABLE embedding_jobs (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED')),
    total_messages  BIGINT DEFAULT 0,
    processed       BIGINT DEFAULT 0,
    failed          BIGINT DEFAULT 0,
    model_name      VARCHAR(100) NOT NULL,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_embedding_jobs_user ON embedding_jobs (user_id);
