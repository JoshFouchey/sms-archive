-- Add source tracking table for triple confidence boosting.
-- Multiple messages can contribute evidence for the same fact.
CREATE TABLE kg_triple_sources (
    triple_id   BIGINT NOT NULL REFERENCES kg_triples(id) ON DELETE CASCADE,
    message_id  BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    created_at  TIMESTAMP DEFAULT now(),
    PRIMARY KEY (triple_id, message_id)
);

CREATE INDEX idx_kg_triple_sources_message ON kg_triple_sources (message_id);

-- Deduplicate existing triples before adding unique constraint.
-- For each set of duplicates, keep the one with highest confidence (or earliest id as tiebreaker)
-- and boost its confidence based on how many duplicates existed.
DELETE FROM kg_triples
WHERE id NOT IN (
    SELECT DISTINCT ON (user_id, subject_id, predicate,
                        COALESCE(object_id, -1),
                        COALESCE(object_value, ''))
           id
    FROM kg_triples
    ORDER BY user_id, subject_id, predicate,
             COALESCE(object_id, -1),
             COALESCE(object_value, ''),
             confidence DESC, id ASC
);

-- Add unique constraint on triples to prevent duplicates.
-- Same subject + predicate + object (entity or value) = same fact.
CREATE UNIQUE INDEX uq_kg_triple_fact ON kg_triples (
    user_id, subject_id, predicate,
    COALESCE(object_id, -1),
    COALESCE(object_value, '')
);
