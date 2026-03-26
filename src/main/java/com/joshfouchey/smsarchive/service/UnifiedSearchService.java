package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.*;
import com.joshfouchey.smsarchive.mapper.MessageMapper;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
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

    @Value("${smsarchive.ai.search.min-score:0.005}")
    private double minScore;

    @Value("${smsarchive.ai.search.time-decay-half-life-days:365}")
    private int timeDecayHalfLifeDays;

    @Value("${smsarchive.ai.search.conversation-dedup:true}")
    private boolean conversationDedup;

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

        // Fetch more results than needed so dedup still returns topK
        int fetchK = conversationDedup ? k * 3 : k;

        UnifiedSearchResult raw = switch (mode) {
            case KEYWORD -> keywordSearch(query, userId, contactId, fetchK);
            case SEMANTIC -> semanticSearch(query, userId, conversationId, contactId, fetchK);
            case HYBRID -> hybridSearch(query, userId, conversationId, contactId, fetchK);
            case AUTO -> hybridSearch(query, userId, conversationId, contactId, fetchK);
        };

        // Apply post-processing: time-decay, min-score filter, conversation dedup
        List<UnifiedSearchHit> processed = postProcess(raw.hits(), k);

        return new UnifiedSearchResult(raw.query(), raw.mode(), processed, processed.size());
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

    // ---- Post-processing pipeline ----

    private List<UnifiedSearchHit> postProcess(List<UnifiedSearchHit> hits, int topK) {
        if (hits.isEmpty()) return hits;

        // 1. Apply time-decay boost
        List<ScoredHit> scored = hits.stream()
                .map(h -> new ScoredHit(h, applyTimeDecay(h.score(), h.message().timestamp())))
                .toList();

        // 2. Filter by min score
        scored = scored.stream()
                .filter(s -> s.score >= minScore)
                .collect(Collectors.toList());

        // 3. Sort by adjusted score
        scored.sort(Comparator.comparingDouble((ScoredHit s) -> s.score).reversed());

        // 4. Conversation dedup: keep best per conversation, count others
        if (conversationDedup) {
            return dedupByConversation(scored, topK);
        }

        return scored.stream()
                .limit(topK)
                .map(s -> new UnifiedSearchHit(s.hit.message(), s.score, s.hit.source(),
                        s.hit.conversationId(), 0))
                .toList();
    }

    private double applyTimeDecay(double score, Instant timestamp) {
        if (timestamp == null || timeDecayHalfLifeDays <= 0) return score;

        long daysSince = Duration.between(timestamp, Instant.now()).toDays();
        if (daysSince <= 0) return score;

        // Exponential decay: score * 2^(-days/halfLife)
        // With 365-day half-life, a 1-year-old message keeps 50% boost, 2-year keeps 25%
        double decayFactor = Math.pow(2.0, -(double) daysSince / timeDecayHalfLifeDays);

        // Blend: 80% original score + 20% time-boosted score
        // This prevents time from dominating relevance
        return score * (0.8 + 0.2 * decayFactor);
    }

    private List<UnifiedSearchHit> dedupByConversation(List<ScoredHit> scored, int topK) {
        // Group by conversation — use contactName as grouping key since we don't have conversationId in DTO
        Map<String, List<ScoredHit>> byConversation = new LinkedHashMap<>();
        for (ScoredHit s : scored) {
            String key = Optional.ofNullable(s.hit.conversationId())
                    .map(String::valueOf)
                    .orElse(Optional.ofNullable(s.hit.message().contactName())
                            .orElse("unknown-" + s.hit.message().id()));
            byConversation.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        // Take the best hit from each conversation, track how many more exist
        List<UnifiedSearchHit> result = new ArrayList<>();
        for (var entry : byConversation.values()) {
            ScoredHit best = entry.get(0); // already sorted by score desc
            int moreCount = entry.size() - 1;
            result.add(new UnifiedSearchHit(
                    best.hit.message(), best.score, best.hit.source(),
                    best.hit.conversationId(), moreCount));
        }

        // Re-sort by score after dedup and take topK
        result.sort(Comparator.comparingDouble((UnifiedSearchHit h) -> h.score()).reversed());
        return result.stream().limit(topK).toList();
    }

    private record ScoredHit(UnifiedSearchHit hit, double score) {}

    // ---- Search implementations ----

    private UnifiedSearchResult keywordSearch(String query, UUID userId, Long contactId, int topK) {
        Page<Message> page = messageRepository.searchByTextUserPaginated(
                query, userId, PageRequest.of(0, topK));

        List<UnifiedSearchHit> hits = page.getContent().stream()
                .map(m -> new UnifiedSearchHit(MessageMapper.toDto(m), 1.0 / (RRF_K + 1), "KEYWORD",
                        m.getConversation() != null ? m.getConversation().getId() : null, 0))
                .toList();

        return new UnifiedSearchResult(query, "KEYWORD", hits, (int) page.getTotalElements());
    }

    private UnifiedSearchResult semanticSearch(
            String query, UUID userId, Long conversationId, Long contactId, int topK) {

        try {
            SemanticSearchResult result = semanticSearchService.search(
                    query, userId, conversationId, contactId, topK);

            List<UnifiedSearchHit> hits = result.hits().stream()
                    .map(h -> new UnifiedSearchHit(h.message(), h.similarity(), "SEMANTIC"))
                    .toList();

            return new UnifiedSearchResult(query, "SEMANTIC", hits, result.totalHits());
        } catch (Exception e) {
            log.warn("Semantic search failed, falling back to keyword: {}", e.getMessage());
            return keywordSearch(query, userId, contactId, topK);
        }
    }

    /**
     * Hybrid search using Reciprocal Rank Fusion (RRF).
     * Runs both keyword and semantic search, then merges with RRF scoring.
     */
    private UnifiedSearchResult hybridSearch(
            String query, UUID userId, Long conversationId, Long contactId, int topK) {

        // Run both searches
        Page<Message> keywordPage = messageRepository.searchByTextUserPaginated(
                query, userId, PageRequest.of(0, topK));

        SemanticSearchResult semanticResult;
        try {
            semanticResult = semanticSearchService.search(
                    query, userId, conversationId, contactId, topK);
        } catch (Exception e) {
            log.warn("Semantic path failed in hybrid search, using keyword only: {}", e.getMessage());
            semanticResult = new SemanticSearchResult(query, List.of(), 0);
        }

        // Build RRF scores by message ID
        Map<Long, Double> rrfScores = new LinkedHashMap<>();
        Map<Long, MessageDto> messageDtos = new LinkedHashMap<>();
        Map<Long, String> sources = new HashMap<>();
        Map<Long, Long> conversationIds = new HashMap<>();

        // Score keyword results
        List<Message> keywordMessages = keywordPage.getContent();
        for (int i = 0; i < keywordMessages.size(); i++) {
            Message m = keywordMessages.get(i);
            Long msgId = m.getId();
            double score = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(msgId, score, Double::sum);
            messageDtos.putIfAbsent(msgId, MessageMapper.toDto(m));
            sources.merge(msgId, "KEYWORD", (a, b) -> "BOTH");
            if (m.getConversation() != null) {
                conversationIds.putIfAbsent(msgId, m.getConversation().getId());
            }
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
                        sources.get(e.getKey()),
                        conversationIds.get(e.getKey()),
                        0))
                .toList();

        return new UnifiedSearchResult(query, "HYBRID", hits, hits.size());
    }
}
