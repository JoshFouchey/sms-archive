package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.model.*;
import com.joshfouchey.smsarchive.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for KG persistence logic: fact hashing, dedup,
 * soft conflict detection, entity disambiguation, and temporal tracking.
 *
 * Uses real Postgres (Testcontainers) because these features rely on
 * pg_trgm, upserts, and native SQL constraints.
 */
@SpringBootTest
@WithMockUser(username = "kgtest")
@EnabledIf("isDockerAvailable")
class KgPersistenceIntegrationTest extends EnhancedPostgresTestContainer {

    static boolean isDockerAvailable() {
        try {
            org.testcontainers.DockerClientFactory.instance().client();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Mock AI beans — not needed for persistence tests, prevents Ollama connection
    @MockBean ChatModel chatModel;
    @MockBean OllamaEmbeddingModel ollamaEmbeddingModel;

    @Autowired KnowledgeGraphExtractionService kgService;
    @Autowired KgEntityRepository entityRepository;
    @Autowired KgTripleRepository tripleRepository;
    @Autowired UserRepository userRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        // Clean KG data (order matters for FK constraints)
        jdbcTemplate.execute("DELETE FROM kg_triple_sources");
        jdbcTemplate.execute("DELETE FROM kg_triples");
        jdbcTemplate.execute("DELETE FROM kg_entity_aliases");
        jdbcTemplate.execute("DELETE FROM kg_entity_contact_links");
        jdbcTemplate.execute("DELETE FROM kg_entities");

        user = userRepository.findByUsername("kgtest").orElseGet(() -> {
            User u = new User();
            u.setUsername("kgtest");
            u.setPasswordHash("$2a$10$dummyhash");
            return userRepository.save(u);
        });
    }

    // ---- Dedup Tests ----

    @Test
    void persistFact_newFact_createsTriple() {
        var fact = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "lives_in", "NYC", "PLACE", 0.9f);
        Instant factDate = Instant.now().minus(30, ChronoUnit.DAYS);

        int newEntities = kgService.persistFact(fact, user, List.of(), factDate);

        assertThat(newEntities).isEqualTo(2); // Tom + NYC
        assertThat(tripleRepository.countByUser(user)).isEqualTo(1);

        KgTriple triple = tripleRepository.findRecentByUser(user.getId(), 1).get(0);
        assertThat(triple.getPredicate()).isEqualTo("lives_in");
        assertThat(triple.getSubject().getCanonicalName()).isEqualTo("Tom");
        assertThat(triple.getFactHash()).isNotNull().hasSize(64);
        assertThat(triple.getFactDate()).isEqualTo(factDate);
        assertThat(triple.getStatus()).isEqualTo("ACTIVE");
        assertThat(triple.getLastSeenAt()).isNotNull();
    }

    @Test
    void persistFact_sameFact_bumpsConfidenceAndLastSeen() {
        var fact = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "lives_in", "NYC", "PLACE", 0.5f);
        Instant factDate = Instant.now().minus(30, ChronoUnit.DAYS);

        // First insertion
        kgService.persistFact(fact, user, List.of(), factDate);
        KgTriple first = tripleRepository.findRecentByUser(user.getId(), 1).get(0);
        float originalConfidence = first.getConfidence();
        Instant originalLastSeen = first.getLastSeenAt();

        // Small delay to ensure lastSeenAt differs
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        // Second insertion of the same fact
        kgService.persistFact(fact, user, List.of(), factDate);

        // Should still be only 1 triple
        assertThat(tripleRepository.countByUser(user)).isEqualTo(1);

        // Confidence should be boosted
        KgTriple updated = tripleRepository.findRecentByUser(user.getId(), 1).get(0);
        assertThat(updated.getConfidence()).isGreaterThan(originalConfidence);
        assertThat(updated.getLastSeenAt()).isAfterOrEqualTo(originalLastSeen);
    }

    @Test
    void persistFact_duplicateDoesNotCreateNewEntities() {
        var fact = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "lives_in", "NYC", "PLACE", 0.8f);
        Instant factDate = Instant.now();

        int created1 = kgService.persistFact(fact, user, List.of(), factDate);
        int created2 = kgService.persistFact(fact, user, List.of(), factDate);

        assertThat(created1).isEqualTo(2); // Tom + NYC
        assertThat(created2).isEqualTo(0); // Reused existing
    }

    // ---- Conflict Detection Tests ----

    @Test
    void persistFact_conflictingFacts_flagsBothInCluster() {
        Instant factDate = Instant.now().minus(60, ChronoUnit.DAYS);

        // First fact: Tom lives in NYC
        var fact1 = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "lives_in", "NYC", "PLACE", 0.9f);
        kgService.persistFact(fact1, user, List.of(), factDate);

        // Second fact: Tom lives in Chicago (conflict!)
        var fact2 = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "lives_in", "Chicago", "PLACE", 0.9f);
        kgService.persistFact(fact2, user, List.of(), factDate.plus(30, ChronoUnit.DAYS));

        // Both triples should exist (not overwritten)
        assertThat(tripleRepository.countByUser(user)).isEqualTo(2);

        // Both should be FLAGGED
        List<KgTriple> triples = tripleRepository.findRecentByUser(user.getId(), 10);
        assertThat(triples).allSatisfy(t -> {
            assertThat(t.getStatus()).isEqualTo("FLAGGED");
            assertThat(t.getConflictClusterId()).isNotNull();
        });

        // Both should share the same conflict cluster
        Long clusterId = triples.get(0).getConflictClusterId();
        assertThat(triples).allSatisfy(t ->
                assertThat(t.getConflictClusterId()).isEqualTo(clusterId));
    }

    @Test
    void persistFact_sameSubjectPredicateSameObject_noConflict() {
        Instant factDate = Instant.now();

        // Two identical facts should NOT create a conflict
        var fact = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "lives_in", "NYC", "PLACE", 0.9f);
        kgService.persistFact(fact, user, List.of(), factDate);
        kgService.persistFact(fact, user, List.of(), factDate);

        List<KgTriple> triples = tripleRepository.findRecentByUser(user.getId(), 10);
        assertThat(triples).hasSize(1);
        assertThat(triples.get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(triples.get(0).getConflictClusterId()).isNull();
    }

    @Test
    void persistFact_differentPredicates_noConflict() {
        Instant factDate = Instant.now();

        // Tom lives_in NYC and Tom works_at Google — different predicates, no conflict
        var fact1 = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "lives_in", "NYC", "PLACE", 0.9f);
        var fact2 = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "works_at", "Google", "ORGANIZATION", 0.9f);
        kgService.persistFact(fact1, user, List.of(), factDate);
        kgService.persistFact(fact2, user, List.of(), factDate);

        List<KgTriple> triples = tripleRepository.findRecentByUser(user.getId(), 10);
        assertThat(triples).hasSize(2);
        assertThat(triples).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo("ACTIVE"));
    }

    // ---- Temporal Tracking Tests ----

    @Test
    void persistFact_setsFactDateFromMessage() {
        Instant messageTimestamp = Instant.parse("2024-06-15T12:00:00Z");
        var fact = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "lives_in", "NYC", "PLACE", 0.9f);

        kgService.persistFact(fact, user, List.of(), messageTimestamp);

        KgTriple triple = tripleRepository.findRecentByUser(user.getId(), 1).get(0);
        assertThat(triple.getFactDate()).isEqualTo(messageTimestamp);
    }

    // ---- Predicate Cardinality Tests ----

    @Test
    void persistFact_pluralPredicate_multipleValuesNoConflict() {
        Instant factDate = Instant.now();

        // Tom owns a Mustang AND a Civic — both should be ACTIVE, no conflict
        var fact1 = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "owns", "Mustang", "VEHICLE", 0.9f);
        var fact2 = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "owns", "Civic", "VEHICLE", 0.9f);
        kgService.persistFact(fact1, user, List.of(), factDate);
        kgService.persistFact(fact2, user, List.of(), factDate);

        List<KgTriple> triples = tripleRepository.findRecentByUser(user.getId(), 10);
        assertThat(triples).hasSize(2);
        assertThat(triples).allSatisfy(t -> {
            assertThat(t.getStatus()).isEqualTo("ACTIVE");
            assertThat(t.getConflictClusterId()).isNull();
        });
    }

    @Test
    void persistFact_singularPredicate_differentValuesConflict() {
        Instant factDate = Instant.now();

        // Tom lives_in NYC then Chicago — singular predicate → conflict
        var fact1 = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "lives_in", "NYC", "PLACE", 0.9f);
        var fact2 = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "lives_in", "Chicago", "PLACE", 0.9f);
        kgService.persistFact(fact1, user, List.of(), factDate);
        kgService.persistFact(fact2, user, List.of(), factDate.plus(30, ChronoUnit.DAYS));

        List<KgTriple> triples = tripleRepository.findRecentByUser(user.getId(), 10);
        assertThat(triples).hasSize(2);
        assertThat(triples).allSatisfy(t -> {
            assertThat(t.getStatus()).isEqualTo("FLAGGED");
            assertThat(t.getConflictClusterId()).isNotNull();
        });
    }

    // ---- Entity Disambiguation Tests ----

    @Test
    void findOrCreateEntity_exactMatch_reusesExisting() {
        KgEntity created = kgService.findOrCreateEntity(user, "Tom", "PERSON");
        KgEntity found = kgService.findOrCreateEntity(user, "Tom", "PERSON");

        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(entityRepository.countByUser(user)).isEqualTo(1);
    }

    @Test
    void findOrCreateEntity_differentTypes_createsSeparate() {
        KgEntity person = kgService.findOrCreateEntity(user, "Portland", "PLACE");
        KgEntity place = kgService.findOrCreateEntity(user, "Portland", "ORGANIZATION");

        assertThat(person.getId()).isNotEqualTo(place.getId());
        assertThat(entityRepository.countByUser(user)).isEqualTo(2);
    }

    @Test
    void findOrCreateEntity_fuzzyMatch_reusesAndAddsAlias() {
        // Create "Thomas Smith"
        KgEntity original = kgService.findOrCreateEntity(user, "Thomas Smith", "PERSON");

        // Now try "Thomas Smit" (high similarity) — should reuse and add alias
        KgEntity fuzzyMatch = kgService.findOrCreateEntity(user, "Thomas Smit", "PERSON");

        assertThat(fuzzyMatch.getId()).isEqualTo(original.getId());
        assertThat(entityRepository.countByUser(user)).isEqualTo(1);

        // Check that the alias was added
        Integer aliasCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kg_entity_aliases WHERE entity_id = ? AND alias = ?",
                Integer.class, original.getId(), "Thomas Smit");
        assertThat(aliasCount).isEqualTo(1);
    }

    @Test
    void findOrCreateEntity_lowSimilarity_createsSeparate() {
        // "Tom" and "Alice" are not similar — should create separate entities
        KgEntity tom = kgService.findOrCreateEntity(user, "Tom", "PERSON");
        KgEntity alice = kgService.findOrCreateEntity(user, "Alice", "PERSON");

        assertThat(tom.getId()).isNotEqualTo(alice.getId());
        assertThat(entityRepository.countByUser(user)).isEqualTo(2);
    }

    @Test
    void findOrCreateEntity_nonPerson_noFuzzyMatching() {
        // Fuzzy matching only applies to PERSON type
        KgEntity place1 = kgService.findOrCreateEntity(user, "New York", "PLACE");
        KgEntity place2 = kgService.findOrCreateEntity(user, "New Yorks", "PLACE");

        // Should create separate entities (no fuzzy match for places)
        assertThat(place1.getId()).isNotEqualTo(place2.getId());
    }
}
