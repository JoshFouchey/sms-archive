-- Migrate embedding column from 768 dimensions (nomic-embed-text) to 1024 dimensions (qwen3-embedding).
-- All existing embeddings must be regenerated since they were created with a different model/vector space.

-- 1. Delete all existing embeddings (incompatible vector space)
DELETE FROM message_embeddings;

-- 2. Drop the HNSW index (must be dropped before altering column type)
DROP INDEX IF EXISTS idx_message_embeddings_vector;

-- 3. Alter column from vector(768) to vector(1024)
ALTER TABLE message_embeddings
    ALTER COLUMN embedding TYPE vector(1024);

-- 4. Update the default model_name
ALTER TABLE message_embeddings
    ALTER COLUMN model_name SET DEFAULT 'qwen3-embedding:0.6b';

-- 5. Rebuild HNSW index with the new dimensions
CREATE INDEX idx_message_embeddings_vector
    ON message_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 24, ef_construction = 256);
