-- Rebuild HNSW index with optimized parameters for better recall.
-- m=24: more connections per node (was 16) — improves search quality
-- ef_construction=256: build with more candidates (was 128) — better index structure
-- Trade-off: ~2x more memory for index, slower rebuild, but significantly better recall.

DROP INDEX IF EXISTS idx_message_embeddings_vector;

CREATE INDEX idx_message_embeddings_vector
    ON message_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 24, ef_construction = 256);
