-- Add pg_trgm extension for fuzzy/similarity search (typo tolerance)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Add GIN index for trigram similarity on message body
CREATE INDEX IF NOT EXISTS messages_body_trgm_idx ON messages USING gin (body gin_trgm_ops);

-- Add GIN index for trigram similarity on conversation names
CREATE INDEX IF NOT EXISTS conversations_name_trgm_idx ON conversations USING gin (name gin_trgm_ops);

-- Note: The full-text search index idx_messages_body_fts already exists from V1__baseline.sql
