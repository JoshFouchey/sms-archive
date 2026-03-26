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
}
