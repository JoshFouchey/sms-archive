package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.*;
import com.joshfouchey.smsarchive.mapper.MessageMapper;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class UnifiedSearchService {

    public enum SearchMode { KEYWORD, SEMANTIC, HYBRID, AUTO }

    private static final int RRF_K = 60;
    private static final Pattern QUOTED_PATTERN = Pattern.compile("\"[^\"]+\"");
    private static final Set<String> QUESTION_WORDS = Set.of(
            "who", "what", "when", "where", "why", "how", "which",
            "did", "does", "was", "were", "is", "are", "can", "could",
            "tell", "find", "show", "remember", "recall");

    private final SemanticSearchService semanticSearchService;
    private final MessageRepository messageRepository;

    public UnifiedSearchService(
            SemanticSearchService semanticSearchService,
            MessageRepository messageRepository) {
        this.semanticSearchService = semanticSearchService;
        this.messageRepository = messageRepository;
    }

    public UnifiedSearchResult search(
            String query,
            SearchMode mode,
            UUID userId,
            Long conversationId,
            Long contactId,
            Integer topK) {

        int k = topK != null ? topK : 20;

        if (mode == SearchMode.AUTO) {
            mode = classifyIntent(query);
        }

        return switch (mode) {
            case KEYWORD -> keywordSearch(query, userId, contactId, k);
            case SEMANTIC -> semanticSearch(query, userId, conversationId, contactId, k);
            case HYBRID -> hybridSearch(query, userId, conversationId, contactId, k);
            case AUTO -> hybridSearch(query, userId, conversationId, contactId, k);
        };
    }

    /**
     * Heuristic intent classifier — no ML needed.
     */
    SearchMode classifyIntent(String query) {
        if (query == null || query.isBlank()) return SearchMode.KEYWORD;

        String lower = query.toLowerCase().trim();

        // Quoted phrases → exact keyword match
        if (QUOTED_PATTERN.matcher(query).find()) return SearchMode.KEYWORD;

        // Very short queries (1-2 words, no question words) → keyword
        String[] words = lower.split("\\s+");
        if (words.length <= 2 && QUESTION_WORDS.stream().noneMatch(lower::startsWith)) {
            return SearchMode.KEYWORD;
        }

        // Question-like or natural language → semantic
        if (QUESTION_WORDS.stream().anyMatch(lower::startsWith)) {
            return SearchMode.SEMANTIC;
        }

        // Longer phrases → hybrid (best of both)
        if (words.length >= 4) return SearchMode.HYBRID;

        // Default: hybrid
        return SearchMode.HYBRID;
    }

    private UnifiedSearchResult keywordSearch(String query, UUID userId, Long contactId, int topK) {
        Page<Message> page = messageRepository.searchByTextUserPaginated(
                query, userId, PageRequest.of(0, topK));

        List<UnifiedSearchHit> hits = page.getContent().stream()
                .map(m -> new UnifiedSearchHit(MessageMapper.toDto(m), 0.0, "KEYWORD"))
                .toList();

        return new UnifiedSearchResult(query, "KEYWORD", hits, (int) page.getTotalElements());
    }

    private UnifiedSearchResult semanticSearch(
            String query, UUID userId, Long conversationId, Long contactId, int topK) {

        SemanticSearchResult result = semanticSearchService.search(
                query, userId, conversationId, contactId, topK);

        List<UnifiedSearchHit> hits = result.hits().stream()
                .map(h -> new UnifiedSearchHit(h.message(), h.similarity(), "SEMANTIC"))
                .toList();

        return new UnifiedSearchResult(query, "SEMANTIC", hits, result.totalHits());
    }

    /**
     * Hybrid search using Reciprocal Rank Fusion (RRF).
     * Runs both keyword and semantic search, then merges with RRF scoring.
     */
    private UnifiedSearchResult hybridSearch(
            String query, UUID userId, Long conversationId, Long contactId, int topK) {

        // Run both searches (semantic fetches more to have good fusion)
        Page<Message> keywordPage = messageRepository.searchByTextUserPaginated(
                query, userId, PageRequest.of(0, topK));

        SemanticSearchResult semanticResult = semanticSearchService.search(
                query, userId, conversationId, contactId, topK);

        // Build RRF scores by message ID
        Map<Long, Double> rrfScores = new LinkedHashMap<>();
        Map<Long, MessageDto> messageDtos = new LinkedHashMap<>();
        Map<Long, String> sources = new HashMap<>();

        // Score keyword results
        List<Message> keywordMessages = keywordPage.getContent();
        for (int i = 0; i < keywordMessages.size(); i++) {
            Long msgId = keywordMessages.get(i).getId();
            double score = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(msgId, score, Double::sum);
            messageDtos.putIfAbsent(msgId, MessageMapper.toDto(keywordMessages.get(i)));
            sources.merge(msgId, "KEYWORD", (a, b) -> "BOTH");
        }

        // Score semantic results
        List<SemanticSearchHit> semanticHits = semanticResult.hits();
        for (int i = 0; i < semanticHits.size(); i++) {
            Long msgId = semanticHits.get(i).message().id();
            double score = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(msgId, score, Double::sum);
            messageDtos.putIfAbsent(msgId, semanticHits.get(i).message());
            sources.merge(msgId, "SEMANTIC", (a, b) -> "BOTH");
        }

        // Sort by RRF score descending and take topK
        List<UnifiedSearchHit> hits = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> new UnifiedSearchHit(
                        messageDtos.get(e.getKey()),
                        e.getValue(),
                        sources.get(e.getKey())))
                .toList();

        return new UnifiedSearchResult(query, "HYBRID", hits, hits.size());
    }
}
