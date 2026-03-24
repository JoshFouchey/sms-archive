package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.SemanticSearchHit;
import com.joshfouchey.smsarchive.dto.SemanticSearchResult;
import com.joshfouchey.smsarchive.mapper.MessageMapper;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.MessageEmbeddingRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Semantic search pipeline: embed query → pgvector ANN → ranked results.
 * Handles Ollama unavailability gracefully by returning empty results.
 */

@Slf4j
@Service
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class SemanticSearchService {

    private final EmbeddingService embeddingService;
    private final MessageEmbeddingRepository embeddingRepository;
    private final MessageRepository messageRepository;

    @Value("${smsarchive.ai.search.default-top-k:20}")
    private int defaultTopK;

    @Value("${smsarchive.ai.search.similarity-threshold:0.65}")
    private double similarityThreshold;

    public SemanticSearchService(
            EmbeddingService embeddingService,
            MessageEmbeddingRepository embeddingRepository,
            MessageRepository messageRepository) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * Semantic search pipeline:
     * 1. Embed user query via Ollama
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
            log.warn("Semantic search unavailable — Ollama embedding failed: {}", e.getMessage());
            return new SemanticSearchResult(naturalLanguageQuery, List.of(), 0);
        }
        String vectorString = EmbeddingService.toVectorString(queryVector);

        // Step 2: ANN search — retrieve raw results from pgvector
        List<Object[]> rawResults;
        if (conversationId != null) {
            rawResults = embeddingRepository.findSimilarInConversation(
                    userId, conversationId, vectorString, k);
        } else if (contactId != null) {
            rawResults = embeddingRepository.findSimilarByContact(
                    userId, contactId, vectorString, k);
        } else {
            rawResults = embeddingRepository.findSimilarMessages(
                    userId, vectorString, k);
        }

        // Step 3: Load full message entities with associations for DTO mapping
        List<Long> messageIds = rawResults.stream()
                .map(row -> ((Number) row[0]).longValue())
                .toList();

        Map<Long, Message> messageMap = messageRepository.findAllById(messageIds).stream()
                .collect(Collectors.toMap(Message::getId, m -> m));

        // Build hits preserving vector search order
        List<SemanticSearchHit> hits = new ArrayList<>();
        for (int i = 0; i < rawResults.size(); i++) {
            Long msgId = messageIds.get(i);
            Message msg = messageMap.get(msgId);
            if (msg == null) continue;

            // Similarity score is the last column in all queries
            Object[] row = rawResults.get(i);
            double similarity = ((Number) row[row.length - 1]).doubleValue();

            if (similarity >= similarityThreshold) {
                hits.add(new SemanticSearchHit(MessageMapper.toDto(msg), similarity));
            }
        }

        return new SemanticSearchResult(naturalLanguageQuery, hits, hits.size());
    }

    /**
     * Embed a query with retry logic (2 attempts with 1s backoff).
     * Handles transient Ollama failures gracefully.
     */
    private float[] embedQueryWithRetry(String query) {
        Exception lastException = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return embeddingService.embedQuery(query);
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
        throw new RuntimeException("Ollama embedding unavailable after 2 attempts", lastException);
    }
}
