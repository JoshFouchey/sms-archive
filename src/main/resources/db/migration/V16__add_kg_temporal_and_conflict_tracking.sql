-- ============================================================================
-- KG Temporal Tracking & Conflict Resolution
-- Adds fact hashing, temporal dating, status lifecycle, and conflict clusters
-- to support the "History of Truth" pattern.
-- ============================================================================

-- Fact hash for dedup: SHA-256 of normalized(subject_name + predicate + object_name/value)
ALTER TABLE kg_triples ADD COLUMN fact_hash VARCHAR(64);

-- When the fact was stated (from the source message timestamp, not extraction time)
ALTER TABLE kg_triples ADD COLUMN fact_date TIMESTAMP;

-- Lifecycle status: ACTIVE → SUPERSEDED | FLAGGED | PENDING_REVIEW
ALTER TABLE kg_triples ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'FLAGGED', 'PENDING_REVIEW'));

-- Pointer to the newer fact that replaced this one
ALTER TABLE kg_triples ADD COLUMN superseded_by BIGINT REFERENCES kg_triples(id) ON DELETE SET NULL;

-- Updated when the same fact is re-extracted from a different message
ALTER TABLE kg_triples ADD COLUMN last_seen_at TIMESTAMP;

-- Groups contradictory facts for the same subject+predicate (e.g., two different addresses for Tom)
ALTER TABLE kg_triples ADD COLUMN conflict_cluster_id BIGINT;

-- Index for fast hash lookups (exact dedup)
CREATE INDEX idx_kg_triples_fact_hash ON kg_triples (fact_hash) WHERE fact_hash IS NOT NULL;

-- Index for conflict detection: find all active facts for a subject+predicate
CREATE INDEX idx_kg_triples_conflict ON kg_triples (subject_id, predicate, status);

-- Index for conflict cluster queries
CREATE INDEX idx_kg_triples_cluster ON kg_triples (conflict_cluster_id) WHERE conflict_cluster_id IS NOT NULL;

-- Backfill status for existing triples
UPDATE kg_triples SET status = 'ACTIVE' WHERE status IS NULL;

-- Backfill last_seen_at from created_at for existing triples
UPDATE kg_triples SET last_seen_at = created_at WHERE last_seen_at IS NULL;
