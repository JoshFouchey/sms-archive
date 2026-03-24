-- ============================================================================
-- Knowledge Graph Schema
-- Stores entities, triples (facts), and aliases extracted from messages.
-- ============================================================================

-- Entities: People, places, things extracted from messages
CREATE TABLE kg_entities (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    canonical_name  TEXT NOT NULL,
    entity_type     VARCHAR(50) NOT NULL
                    CHECK (entity_type IN (
                        'PERSON','PLACE','ORGANIZATION','OBJECT','EVENT',
                        'CONCEPT','FOOD','VEHICLE','PET','MEDICAL','DATE'
                    )),
    description     TEXT,
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMP DEFAULT now(),
    updated_at      TIMESTAMP DEFAULT now(),
    CONSTRAINT uq_entity_user_name_type UNIQUE (user_id, canonical_name, entity_type)
);

CREATE INDEX idx_kg_entities_user ON kg_entities (user_id);
CREATE INDEX idx_kg_entities_type ON kg_entities (user_id, entity_type);
CREATE INDEX idx_kg_entities_name_trgm ON kg_entities USING gin (canonical_name gin_trgm_ops);

CREATE TRIGGER trg_kg_entities_updated_at
    BEFORE UPDATE ON kg_entities
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Entity aliases for resolution ("Tom", "Tommy", "Dad" → same entity)
CREATE TABLE kg_entity_aliases (
    id          BIGSERIAL PRIMARY KEY,
    entity_id   BIGINT NOT NULL REFERENCES kg_entities(id) ON DELETE CASCADE,
    alias       TEXT NOT NULL,
    source      VARCHAR(50) DEFAULT 'EXTRACTED'
                CHECK (source IN ('EXTRACTED','USER_DEFINED','CONTACT_LINKED')),
    confidence  REAL DEFAULT 0.8,
    created_at  TIMESTAMP DEFAULT now(),
    CONSTRAINT uq_entity_alias UNIQUE (entity_id, alias)
);

CREATE INDEX idx_kg_aliases_entity ON kg_entity_aliases (entity_id);
CREATE INDEX idx_kg_aliases_alias_trgm ON kg_entity_aliases USING gin (alias gin_trgm_ops);

-- Triples: Subject → Predicate → Object
CREATE TABLE kg_triples (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject_id          BIGINT NOT NULL REFERENCES kg_entities(id) ON DELETE CASCADE,
    predicate           VARCHAR(100) NOT NULL,
    object_id           BIGINT REFERENCES kg_entities(id) ON DELETE CASCADE,
    object_value        TEXT,
    confidence          REAL DEFAULT 0.8,
    source_message_id   BIGINT REFERENCES messages(id) ON DELETE SET NULL,
    extracted_text      TEXT,
    is_verified         BOOLEAN DEFAULT FALSE,
    is_negated          BOOLEAN DEFAULT FALSE,
    valid_from          TIMESTAMP,
    valid_until         TIMESTAMP,
    created_at          TIMESTAMP DEFAULT now(),
    updated_at          TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_kg_triples_subject ON kg_triples (subject_id);
CREATE INDEX idx_kg_triples_object ON kg_triples (object_id);
CREATE INDEX idx_kg_triples_predicate ON kg_triples (user_id, predicate);
CREATE INDEX idx_kg_triples_source ON kg_triples (source_message_id);
CREATE INDEX idx_kg_triples_user ON kg_triples (user_id);

CREATE TRIGGER trg_kg_triples_updated_at
    BEFORE UPDATE ON kg_triples
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Link KG entities to contacts table
CREATE TABLE kg_entity_contact_links (
    entity_id   BIGINT NOT NULL REFERENCES kg_entities(id) ON DELETE CASCADE,
    contact_id  BIGINT NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    confidence  REAL DEFAULT 0.9,
    created_at  TIMESTAMP DEFAULT now(),
    PRIMARY KEY (entity_id, contact_id)
);

CREATE INDEX idx_kg_ecl_contact ON kg_entity_contact_links (contact_id);

-- KG extraction job tracking
CREATE TABLE kg_extraction_jobs (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED')),
    total_messages  BIGINT DEFAULT 0,
    processed       BIGINT DEFAULT 0,
    triples_found   BIGINT DEFAULT 0,
    entities_found  BIGINT DEFAULT 0,
    model_name      VARCHAR(100) NOT NULL,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_kg_extraction_jobs_user ON kg_extraction_jobs (user_id);
