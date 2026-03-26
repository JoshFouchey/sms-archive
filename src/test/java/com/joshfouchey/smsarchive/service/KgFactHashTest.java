package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for KG fact hash generation and extracted fact record.
 * Pure logic — no Spring context or database needed.
 */
@ExtendWith(MockitoExtension.class)
class KgFactHashTest {

    @Mock ChatModel chatModel;
    @Mock MessageRepository messageRepository;
    @Mock KgEntityRepository entityRepository;
    @Mock KgTripleRepository tripleRepository;
    @Mock KgEntityAliasRepository aliasRepository;
    @Mock KgExtractionJobRepository jobRepository;
    @Mock TaskExecutor taskExecutor;
    @Mock TransactionTemplate transactionTemplate;
    @Mock JdbcTemplate jdbcTemplate;

    private KnowledgeGraphExtractionService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeGraphExtractionService(
                chatModel, messageRepository, entityRepository, tripleRepository,
                aliasRepository, jobRepository, taskExecutor, transactionTemplate,
                jdbcTemplate, new ObjectMapper());
    }

    @Test
    void hash_isDeterministic() {
        String hash1 = service.generateFactHash("Tom", "lives_in", "NYC");
        String hash2 = service.generateFactHash("Tom", "lives_in", "NYC");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex
    }

    @Test
    void hash_isCaseInsensitive() {
        String hash1 = service.generateFactHash("Tom", "lives_in", "NYC");
        String hash2 = service.generateFactHash("tom", "LIVES_IN", "nyc");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hash_differentFactsDifferentHashes() {
        String hash1 = service.generateFactHash("Tom", "lives_in", "NYC");
        String hash2 = service.generateFactHash("Tom", "lives_in", "Chicago");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hash_subjectOrderMatters() {
        String hash1 = service.generateFactHash("Tom", "lives_in", "NYC");
        String hash2 = service.generateFactHash("NYC", "lives_in", "Tom");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hash_predicateMatters() {
        String hash1 = service.generateFactHash("Tom", "lives_in", "NYC");
        String hash2 = service.generateFactHash("Tom", "works_at", "NYC");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hash_emptyObject_isValid() {
        String hash = service.generateFactHash("Tom", "lives_in", "");
        assertThat(hash).isNotNull().hasSize(64);
    }

    @Test
    void extractedFact_recordCreation() {
        var fact = new KnowledgeGraphExtractionService.ExtractedFact(
                "Tom", "PERSON", "lives_in", "NYC", "PLACE", 0.9f);
        assertThat(fact.subject()).isEqualTo("Tom");
        assertThat(fact.subjectType()).isEqualTo("PERSON");
        assertThat(fact.predicate()).isEqualTo("lives_in");
        assertThat(fact.object()).isEqualTo("NYC");
        assertThat(fact.objectType()).isEqualTo("PLACE");
        assertThat(fact.confidence()).isEqualTo(0.9f);
    }

    // ---- JSON sanitization / parsing tests ----

    @Test
    void parseFacts_equalsSignSeparator() {
        // phi4-mini's most common quirk: "object="value" instead of "object":"value"
        String llmOutput = """
                [{"subject":"Tom","predicate":"works_at","object="Fiat","object_type":"ORGANIZATION","confidence":0.9}]""";
        var facts = service.parseFacts(llmOutput);
        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).subject()).isEqualTo("Tom");
        assertThat(facts.get(0).object()).isEqualTo("Fiat");
    }

    @Test
    void parseFacts_multipleEqualsSignFields() {
        String llmOutput = """
                [{"subject":"Me","predicate":"lives_in","object="Portland","object_type="PLACE","confidence":1}]""";
        var facts = service.parseFacts(llmOutput);
        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).object()).isEqualTo("Portland");
    }

    @Test
    void parseFacts_normalJsonStillWorks() {
        String llmOutput = """
                [{"subject":"Tom","subject_type":"PERSON","predicate":"lives_in","object":"NYC","object_type":"PLACE","confidence":0.9}]""";
        var facts = service.parseFacts(llmOutput);
        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).subject()).isEqualTo("Tom");
        assertThat(facts.get(0).object()).isEqualTo("NYC");
    }

    @Test
    void parseFacts_emptyArray_returnsEmpty() {
        assertThat(service.parseFacts("[]")).isEmpty();
        assertThat(service.parseFacts("")).isEmpty();
        assertThat(service.parseFacts(null)).isEmpty();
    }

    @Test
    void parseFacts_markdownFences() {
        String llmOutput = """
                ```json
                [{"subject":"Tom","predicate":"owns","object":"Mustang","confidence":0.9}]
                ```""";
        var facts = service.parseFacts(llmOutput);
        assertThat(facts).hasSize(1);
    }

    @Test
    void parseFacts_trailingComma() {
        String llmOutput = """
                [{"subject":"Tom","predicate":"owns","object":"Mustang","confidence":0.9},]""";
        var facts = service.parseFacts(llmOutput);
        assertThat(facts).hasSize(1);
    }

    @Test
    void parseFacts_recoversIndividualObjects() {
        // One valid object, one broken — should recover the valid one
        String llmOutput = """
                [{"subject":"Tom","predicate":"owns","object":"Mustang","confidence":0.9},
                 {"subject":"broken JSON missing closing}]""";
        var facts = service.parseFacts(llmOutput);
        assertThat(facts).hasSizeGreaterThanOrEqualTo(1);
        assertThat(facts.get(0).subject()).isEqualTo("Tom");
    }

    // ---- Predicate normalization tests ----

    @Test
    void normalizePredicate_canonical() {
        assertThat(service.normalizePredicate("lives_in")).isEqualTo("lives_in");
        assertThat(service.normalizePredicate("owns")).isEqualTo("owns");
    }

    @Test
    void normalizePredicate_aliases() {
        assertThat(service.normalizePredicate("sister_of")).isEqualTo("sibling_of");
        assertThat(service.normalizePredicate("favorite_show")).isEqualTo("watches");
        assertThat(service.normalizePredicate("wants_to_visit")).isEqualTo("wants");
        assertThat(service.normalizePredicate("was_diagnosed_with")).isEqualTo("diagnosed_with");
        assertThat(service.normalizePredicate("loves")).isEqualTo("likes");
        assertThat(service.normalizePredicate("worked_at")).isEqualTo("works_at");
        assertThat(service.normalizePredicate("anniversary_with")).isEqualTo("married_to");
    }

    @Test
    void normalizePredicate_compoundPredicates() {
        // phi4-mini creates "plans_to_grab_dinner" — should match "plans_to" prefix
        assertThat(service.normalizePredicate("plans_to_grab_dinner")).isEqualTo("plans_to");
        assertThat(service.normalizePredicate("traveled_to_japan")).isEqualTo("traveled_to");
    }

    @Test
    void normalizePredicate_baseFormVerbs() {
        // phi4-mini sometimes uses base form without trailing 's'
        assertThat(service.normalizePredicate("live_in")).isEqualTo("lives_in");
        assertThat(service.normalizePredicate("own")).isEqualTo("owns");
        assertThat(service.normalizePredicate("play")).isEqualTo("plays");
        assertThat(service.normalizePredicate("watch")).isEqualTo("watches");
        assertThat(service.normalizePredicate("teach_at")).isEqualTo("teaches");
    }

    @Test
    void normalizePredicate_stillPrefix() {
        assertThat(service.normalizePredicate("still_plays")).isEqualTo("plays");
        assertThat(service.normalizePredicate("still_likes")).isEqualTo("likes");
    }

    @Test
    void normalizePredicate_strippedPrefixes() {
        assertThat(service.normalizePredicate("is_allergic_to")).isEqualTo("is_allergic_to");
        assertThat(service.normalizePredicate("has_pet")).isEqualTo("has_pet");
    }

    @Test
    void normalizePredicate_unknown_returnsRelatedTo() {
        assertThat(service.normalizePredicate("is_part_of")).isEqualTo("related_to");
        assertThat(service.normalizePredicate("has_planned_something_big")).isEqualTo("related_to");
    }
}
