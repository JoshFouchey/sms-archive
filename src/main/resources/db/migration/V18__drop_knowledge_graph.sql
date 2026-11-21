-- Remove Knowledge Graph feature: drop all KG tables
DROP TABLE IF EXISTS kg_triple_sources CASCADE;
DROP TABLE IF EXISTS kg_processed_messages CASCADE;
DROP TABLE IF EXISTS kg_entity_contact_links CASCADE;
DROP TABLE IF EXISTS kg_entity_aliases CASCADE;
DROP TABLE IF EXISTS kg_triples CASCADE;
DROP TABLE IF EXISTS kg_entities CASCADE;
DROP TABLE IF EXISTS kg_extraction_jobs CASCADE;
