package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.EmbeddingJobDto;
import com.joshfouchey.smsarchive.dto.EmbeddingStatsDto;
import com.joshfouchey.smsarchive.dto.SemanticSearchResult;
import com.joshfouchey.smsarchive.service.CurrentUserProvider;
import com.joshfouchey.smsarchive.service.EmbeddingService;
import com.joshfouchey.smsarchive.service.SemanticSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/search")
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class SemanticSearchController {

    private final SemanticSearchService searchService;
    private final EmbeddingService embeddingService;
    private final CurrentUserProvider currentUserProvider;

    public SemanticSearchController(
            SemanticSearchService searchService,
            EmbeddingService embeddingService,
            CurrentUserProvider currentUserProvider) {
        this.searchService = searchService;
        this.embeddingService = embeddingService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/semantic")
    public SemanticSearchResult semanticSearch(
            @RequestParam String q,
            @RequestParam(required = false) Long conversationId,
            @RequestParam(required = false) Long contactId,
            @RequestParam(required = false) Integer topK) {
        var user = currentUserProvider.getCurrentUser();
        return searchService.search(q, user.getId(), conversationId, contactId, topK);
    }

    @PostMapping("/embeddings/start")
    public EmbeddingJobDto startEmbeddingJob() {
        var user = currentUserProvider.getCurrentUser();
        UUID jobId = embeddingService.startBatchEmbedding(user);
        return embeddingService.getJobStatus(jobId, user);
    }

    @GetMapping("/embeddings/status/{jobId}")
    public EmbeddingJobDto getJobStatus(@PathVariable UUID jobId) {
        var user = currentUserProvider.getCurrentUser();
        return embeddingService.getJobStatus(jobId, user);
    }

    @PostMapping("/embeddings/cancel/{jobId}")
    public ResponseEntity<Void> cancelJob(@PathVariable UUID jobId) {
        embeddingService.cancelJob(jobId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/embeddings/stats")
    public EmbeddingStatsDto getEmbeddingStats() {
        var user = currentUserProvider.getCurrentUser();
        return embeddingService.getStats(user);
    }

    @GetMapping("/embeddings/history")
    public List<EmbeddingJobDto> getJobHistory() {
        var user = currentUserProvider.getCurrentUser();
        return embeddingService.getJobHistory(user);
    }
}
