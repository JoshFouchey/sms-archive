package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.SemanticSearchHit;
import com.joshfouchey.smsarchive.dto.SemanticSearchResult;
import com.joshfouchey.smsarchive.mapper.MessageMapper;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.MessageEmbeddingRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Semantic search pipeline: embed query → pgvector ANN → ranked results.
 * Handles LLM server unavailability gracefully by returning empty results.
 */

@Slf4j
@Service
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class SemanticSearchService {

    private final EmbeddingService embeddingService;
    private final MessageEmbeddingRepository embeddingRepository;
    private final MessageRepository messageRepository;
    private final TransactionTemplate transactionTemplate;

    // Small bounded cache of query string → embedding vector. Repeated/paginated/identical
    // searches skip the embedding round-trip (and any model reload that would entail).
    private final Cache<String, float[]> queryEmbeddingCache = Caffeine.newBuilder()
            .maximumSize(256)
            .expireAfterWrite(java.time.Duration.ofMinutes(30))
            .build();

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${smsarchive.ai.search.default-top-k:20}")
    private int defaultTopK;

    @Value("${smsarchive.ai.search.similarity-threshold:0.30}")
    private double similarityThreshold;

    // HNSW recall parameter. pgvector defaults to 40; if it is below the number of
    // candidates fetched, recall silently degrades. Applied per vector query below.
    @Value("${smsarchive.ai.search.hnsw-ef-search:100}")
    private int hnswEfSearch;

    @Value("${smsarchive.ai.embedding.model:qwen3-embedding:0.6b}")
    private String modelName;

    public SemanticSearchService(
            EmbeddingService embeddingService,
            MessageEmbeddingRepository embeddingRepository,
            MessageRepository messageRepository,
            PlatformTransactionManager transactionManager) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.messageRepository = messageRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);
    }

    /**
     * Semantic search pipeline:
     * 1. Embed user query via embedding model
     * 2. Vector ANN search via pgvector HNSW index
     * 3. Return top-K results with similarity scores
     */
    public SemanticSearchResult search(
            String naturalLanguageQuery,
            UUID userId,
            Long conversationId,
            Long contactId,
            Integer topK) {

        int k = topK != null ? topK : defaultTopK;

        // Step 1: Embed the query (with retry and graceful failure)
        float[] queryVector;
        try {
            queryVector = embedQueryWithRetry(naturalLanguageQuery);
        } catch (Exception e) {
            log.warn("Semantic search unavailable — LLM server embedding failed: {}", e.getMessage());
            return new SemanticSearchResult(naturalLanguageQuery, List.of(), 0);
        }
        String vectorString = EmbeddingService.toVectorString(queryVector);

        // Step 2: ANN search — retrieve raw results from pgvector.
        // Run inside one short transaction so `SET LOCAL hnsw.ef_search` applies to the
        // vector query on the same connection; ef_search must be >= the rows fetched (k)
        // or HNSW recall degrades. The embedding HTTP call already completed above, so no
        // DB connection is held during a (possibly slow) model load. Falls back to the
        // server default if the SET fails, so behavior is never worse than before.
        final int efSearch = Math.max(hnswEfSearch, k);
        List<Object[]> rawResults;
        try {
            rawResults = transactionTemplate.execute(status -> {
                entityManager.createNativeQuery("SET LOCAL hnsw.ef_search = " + efSearch).executeUpdate();
                return runAnnQuery(userId, conversationId, contactId, vectorString, k);
            });
        } catch (Exception e) {
            log.warn("Could not apply hnsw.ef_search={}, falling back to server default: {}", efSearch, e.getMessage());
            rawResults = runAnnQuery(userId, conversationId, contactId, vectorString, k);
        }

        // Step 3: Deduplicate by message_id (chunks of the same message may appear
        // multiple times — keep the best similarity, which is first since results are
        // ordered by cosine distance ascending)
        List<Object[]> dedupedResults = new ArrayList<>();
        Set<Long> seenMessageIds = new HashSet<>();
        for (Object[] row : rawResults) {
            Long msgId = ((Number) row[0]).longValue();
            if (seenMessageIds.add(msgId)) {
                dedupedResults.add(row);
            }
        }

        // Step 4: Load full message entities with associations for DTO mapping
        List<Long> messageIds = dedupedResults.stream()
                .map(row -> ((Number) row[0]).longValue())
                .toList();

        Map<Long, Message> messageMap = messageRepository.findAllById(messageIds).stream()
                .collect(Collectors.toMap(Message::getId, m -> m));

        // Build hits preserving vector search order
        List<SemanticSearchHit> hits = new ArrayList<>();
        for (int i = 0; i < dedupedResults.size(); i++) {
            Long msgId = messageIds.get(i);
            Message msg = messageMap.get(msgId);
            if (msg == null) continue;

            Object[] row = dedupedResults.get(i);
            double similarity = ((Number) row[row.length - 1]).doubleValue();

            if (i < 3) {
                log.info("Semantic top-{}: msgId={} similarity={} body='{}'",
                        i + 1, msgId, String.format("%.4f", similarity),
                        msg.getBody() != null ? msg.getBody().substring(0, Math.min(60, msg.getBody().length())) : "null");
            }

            if (similarity >= similarityThreshold) {
                hits.add(new SemanticSearchHit(MessageMapper.toDto(msg), similarity));
            }
        }

        log.info("Semantic search '{}': {} raw results, {} after chunk dedup, {} above threshold ({})",
                naturalLanguageQuery.substring(0, Math.min(50, naturalLanguageQuery.length())),
                rawResults.size(), dedupedResults.size(), hits.size(), similarityThreshold);

        return new SemanticSearchResult(naturalLanguageQuery, hits, hits.size());
    }

    /** Dispatch to the correct pgvector ANN query based on optional scoping. */
    private List<Object[]> runAnnQuery(UUID userId, Long conversationId, Long contactId, String vectorString, int k) {
        if (conversationId != null) {
            return embeddingRepository.findSimilarInConversation(userId, modelName, conversationId, vectorString, k);
        } else if (contactId != null) {
            return embeddingRepository.findSimilarByContact(userId, modelName, contactId, vectorString, k);
        } else {
            return embeddingRepository.findSimilarMessages(userId, modelName, vectorString, k);
        }
    }

    /**
     * Embed a query with retry logic (2 attempts with 1s backoff).
     * Handles transient LLM server failures gracefully.
     */
    private float[] embedQueryWithRetry(String query) {
        String cacheKey = query == null ? "" : query.strip();
        float[] cached = queryEmbeddingCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Query embedding cache hit");
            return cached;
        }
        Exception lastException = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                float[] vector = embeddingService.embedQuery(query);
                queryEmbeddingCache.put(cacheKey, vector);
                return vector;
            } catch (Exception e) {
                lastException = e;
                log.debug("Embedding attempt {} failed: {}", attempt + 1, e.getMessage());
                if (attempt < 1) {
                    try { Thread.sleep(1000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                }
            }
        }
        throw new RuntimeException("LLM server embedding unavailable after 2 attempts", lastException);
    }
}
