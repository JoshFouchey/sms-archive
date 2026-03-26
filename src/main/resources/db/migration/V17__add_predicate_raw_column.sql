-- V17: Add predicate_raw column for open-schema predicate capture.
-- predicate_raw stores the exact predicate from the LLM; predicate stores the normalized canonical form.
-- When normalization fails, predicate = predicate_raw (no data loss).

ALTER TABLE kg_triples ADD COLUMN IF NOT EXISTS predicate_raw VARCHAR(200);

-- Backfill: existing rows already have normalized predicates, copy to raw
UPDATE kg_triples SET predicate_raw = predicate WHERE predicate_raw IS NULL;

-- Index for discovering un-normalized predicates
CREATE INDEX IF NOT EXISTS idx_kg_triples_predicate_raw ON kg_triples (user_id, predicate_raw);
