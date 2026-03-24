package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.MergeSuggestion;
import com.joshfouchey.smsarchive.dto.ResolutionResult;
import com.joshfouchey.smsarchive.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 3-layer entity resolution:
 *   Layer 1: Auto-merge exact case-insensitive duplicates
 *   Layer 2: Link PERSON entities to contacts by name similarity
 *   Layer 3: Suggest fuzzy-match merges for user review
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class EntityResolutionService {

    private final KnowledgeGraphService kgService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public EntityResolutionService(
            KnowledgeGraphService kgService,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this.kgService = kgService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Run all 3 resolution layers. Safe to call repeatedly (idempotent).
     */
    public ResolutionResult runResolution(User user) {
        int merged = autoMergeExactDuplicates(user);
        int linked = linkEntitiesToContacts(user);
        List<MergeSuggestion> suggestions = findMergeSuggestions(user, 0.6);

        log.info("Entity resolution for {}: {} auto-merged, {} contacts linked, {} suggestions",
                user.getUsername(), merged, linked, suggestions.size());

        return new ResolutionResult(merged, linked, suggestions);
    }

    /**
     * Layer 1: Auto-merge entities with identical names (case-insensitive) and same type.
     * Keeps the entity with the lower ID as primary.
     */
    public int autoMergeExactDuplicates(User user) {
        // Find case-insensitive duplicate pairs
        List<long[]> duplicatePairs = jdbcTemplate.query("""
                SELECT e1.id AS keep_id, e2.id AS merge_id
                FROM kg_entities e1
                JOIN kg_entities e2
                  ON e1.user_id = e2.user_id
                  AND e1.entity_type = e2.entity_type
                  AND LOWER(TRIM(e1.canonical_name)) = LOWER(TRIM(e2.canonical_name))
                  AND e1.id < e2.id
                WHERE e1.user_id = ?
                ORDER BY e1.id
                """,
                (rs, rowNum) -> new long[]{rs.getLong("keep_id"), rs.getLong("merge_id")},
                user.getId());

        if (duplicatePairs.isEmpty()) return 0;

        int merged = 0;
        for (long[] pair : duplicatePairs) {
            try {
                kgService.mergeEntities(user, pair[0], pair[1]);
                merged++;
                log.debug("Auto-merged entity {} into {} (case-insensitive duplicate)", pair[1], pair[0]);
            } catch (Exception e) {
                log.debug("Skipping auto-merge {}->{}: {}", pair[1], pair[0], e.getMessage());
            }
        }
        return merged;
    }

    /**
     * Layer 2: Link PERSON entities to contacts using name similarity.
     * Uses pg_trgm similarity() with a threshold of 0.5.
     */
    public int linkEntitiesToContacts(User user) {
        // Find PERSON entities not yet linked, that match a contact name
        List<Object[]> matches = jdbcTemplate.query("""
                SELECT e.id AS entity_id, c.id AS contact_id,
                       similarity(LOWER(e.canonical_name), LOWER(c.name)) AS sim
                FROM kg_entities e
                CROSS JOIN contacts c
                WHERE e.user_id = ?
                  AND c.user_id = ?
                  AND e.entity_type = 'PERSON'
                  AND c.name IS NOT NULL AND c.name != ''
                  AND similarity(LOWER(e.canonical_name), LOWER(c.name)) > 0.5
                  AND NOT EXISTS (
                      SELECT 1 FROM kg_entity_contact_links ecl
                      WHERE ecl.entity_id = e.id AND ecl.contact_id = c.id
                  )
                ORDER BY sim DESC
                """,
                (rs, rowNum) -> new Object[]{
                        rs.getLong("entity_id"),
                        rs.getLong("contact_id"),
                        rs.getFloat("sim")
                },
                user.getId(), user.getId());

        if (matches.isEmpty()) return 0;

        int linked = 0;
        for (Object[] match : matches) {
            Long entityId = (Long) match[0];
            Long contactId = (Long) match[1];
            Float confidence = (Float) match[2];

            try {
                jdbcTemplate.update("""
                        INSERT INTO kg_entity_contact_links (entity_id, contact_id, confidence)
                        VALUES (?, ?, ?)
                        ON CONFLICT (entity_id, contact_id) DO NOTHING
                        """,
                        entityId, contactId, confidence);
                linked++;
            } catch (Exception e) {
                log.debug("Failed to link entity {} to contact {}: {}", entityId, contactId, e.getMessage());
            }
        }
        return linked;
    }

    /**
     * Layer 3: Find probable duplicates using fuzzy string similarity.
     * Returns suggestions for user review — does NOT auto-merge.
     */
    public List<MergeSuggestion> findMergeSuggestions(User user, double threshold) {
        List<MergeSuggestion> suggestions = new ArrayList<>();

        // Same-type entities with similar names
        suggestions.addAll(jdbcTemplate.query("""
                SELECT e1.id AS id1, e1.canonical_name AS name1,
                       e2.id AS id2, e2.canonical_name AS name2,
                       e1.entity_type,
                       similarity(LOWER(e1.canonical_name), LOWER(e2.canonical_name)) AS sim
                FROM kg_entities e1
                JOIN kg_entities e2
                  ON e1.user_id = e2.user_id
                  AND e1.entity_type = e2.entity_type
                  AND e1.id < e2.id
                WHERE e1.user_id = ?
                  AND LOWER(e1.canonical_name) != LOWER(e2.canonical_name)
                  AND similarity(LOWER(e1.canonical_name), LOWER(e2.canonical_name)) > ?
                ORDER BY sim DESC
                LIMIT 50
                """,
                (rs, rowNum) -> new MergeSuggestion(
                        rs.getLong("id1"), rs.getString("name1"),
                        rs.getLong("id2"), rs.getString("name2"),
                        rs.getString("entity_type"),
                        rs.getDouble("sim"),
                        "FUZZY_NAME"),
                user.getId(), threshold));

        // Cross-check aliases against other entities' canonical names
        suggestions.addAll(jdbcTemplate.query("""
                SELECT e1.id AS id1, e1.canonical_name AS name1,
                       e2.id AS id2, e2.canonical_name AS name2,
                       e1.entity_type,
                       similarity(LOWER(a.alias), LOWER(e2.canonical_name)) AS sim
                FROM kg_entity_aliases a
                JOIN kg_entities e1 ON a.entity_id = e1.id
                JOIN kg_entities e2 ON e1.user_id = e2.user_id
                  AND e1.entity_type = e2.entity_type
                  AND e1.id != e2.id
                WHERE e1.user_id = ?
                  AND similarity(LOWER(a.alias), LOWER(e2.canonical_name)) > ?
                  AND LOWER(e1.canonical_name) != LOWER(e2.canonical_name)
                ORDER BY sim DESC
                LIMIT 50
                """,
                (rs, rowNum) -> new MergeSuggestion(
                        rs.getLong("id1"), rs.getString("name1"),
                        rs.getLong("id2"), rs.getString("name2"),
                        rs.getString("entity_type"),
                        rs.getDouble("sim"),
                        "ALIAS_MATCH"),
                user.getId(), threshold));

        // Deduplicate suggestions (same pair from different reasons)
        return suggestions.stream()
                .distinct()
                .sorted(Comparator.comparingDouble(MergeSuggestion::similarity).reversed())
                .limit(50)
                .toList();
    }

    /**
     * Get just the suggestions without running auto-merge.
     */
    public List<MergeSuggestion> getSuggestions(User user) {
        return findMergeSuggestions(user, 0.6);
    }
}
