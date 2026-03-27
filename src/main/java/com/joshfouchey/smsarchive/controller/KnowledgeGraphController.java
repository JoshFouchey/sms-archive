package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.*;
import com.joshfouchey.smsarchive.service.CurrentUserProvider;
import com.joshfouchey.smsarchive.service.EntityResolutionService;
import com.joshfouchey.smsarchive.service.KnowledgeGraphExtractionService;
import com.joshfouchey.smsarchive.service.KnowledgeGraphService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.joshfouchey.smsarchive.util.InputLimits.*;

@RestController
@RequestMapping("/api/knowledge-graph")
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;
    private final KnowledgeGraphExtractionService extractionService;
    private final EntityResolutionService resolutionService;
    private final CurrentUserProvider currentUserProvider;

    public KnowledgeGraphController(KnowledgeGraphService knowledgeGraphService,
                                    KnowledgeGraphExtractionService extractionService,
                                    EntityResolutionService resolutionService,
                                    CurrentUserProvider currentUserProvider) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.extractionService = extractionService;
        this.resolutionService = resolutionService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/entities")
    public List<KgEntityDto> getEntities(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search) {
        var user = currentUserProvider.getCurrentUser();
        return knowledgeGraphService.getEntities(user, type, truncate(search, SEARCH_QUERY_MAX));
    }

    @GetMapping("/entities/{id}")
    public ResponseEntity<KgEntityDto> getEntity(@PathVariable Long id) {
        var user = currentUserProvider.getCurrentUser();
        try {
            return ResponseEntity.ok(knowledgeGraphService.getEntity(user, id));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/entities/{id}/facts")
    public ResponseEntity<List<KgTripleDto>> getEntityFacts(@PathVariable Long id) {
        var user = currentUserProvider.getCurrentUser();
        try {
            return ResponseEntity.ok(knowledgeGraphService.getEntityFacts(user, id));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/triples")
    public List<KgTripleDto> getTriplesByPredicate(@RequestParam String predicate) {
        var user = currentUserProvider.getCurrentUser();
        return knowledgeGraphService.getTriplesByPredicate(user, predicate);
    }

    @GetMapping("/triples/recent")
    public List<KgTripleDto> getRecentTriples(@RequestParam(defaultValue = "20") int limit) {
        var user = currentUserProvider.getCurrentUser();
        return knowledgeGraphService.getRecentTriples(user, Math.min(limit, 100));
    }

    @GetMapping("/contacts/{contactId}/facts")
    public List<KgTripleDto> getContactFacts(@PathVariable Long contactId) {
        var user = currentUserProvider.getCurrentUser();
        return knowledgeGraphService.getContactFacts(user, contactId);
    }

    @PostMapping("/entities/merge")
    public ResponseEntity<KgEntityDto> mergeEntities(@RequestBody MergeRequest request) {
        var user = currentUserProvider.getCurrentUser();
        try {
            return ResponseEntity.ok(
                    knowledgeGraphService.mergeEntities(user, request.primaryId(), request.mergeFromId()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/graph")
    public ResponseEntity<KnowledgeGraphDto> getGraph(
            @RequestParam(required = false) Long entityId,
            @RequestParam(defaultValue = "2") int depth,
            @RequestParam(defaultValue = "100") int maxNodes) {
        var user = currentUserProvider.getCurrentUser();
        try {
            return ResponseEntity.ok(knowledgeGraphService.getGraph(user, entityId, depth, maxNodes));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/stats")
    public Map<String, Long> getStats() {
        var user = currentUserProvider.getCurrentUser();
        return knowledgeGraphService.getStats(user);
    }

    // ---- Extraction job endpoints ----

    @PostMapping("/extraction/start")
    public ResponseEntity<KgExtractionJobDto> startExtraction() {
        var user = currentUserProvider.getCurrentUser();
        try {
            UUID jobId = extractionService.startExtraction(user);
            return ResponseEntity.ok(extractionService.getJobStatus(jobId, user));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/extraction/jobs")
    public List<KgExtractionJobDto> getExtractionJobs() {
        var user = currentUserProvider.getCurrentUser();
        return extractionService.getJobHistory(user);
    }

    @GetMapping("/extraction/jobs/{id}")
    public ResponseEntity<KgExtractionJobDto> getExtractionJob(@PathVariable UUID id) {
        var user = currentUserProvider.getCurrentUser();
        try {
            return ResponseEntity.ok(extractionService.getJobStatus(id, user));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/extraction/jobs/{id}/cancel")
    public ResponseEntity<Void> cancelExtraction(@PathVariable UUID id) {
        extractionService.cancelJob(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetKnowledgeGraph() {
        var user = currentUserProvider.getCurrentUser();
        var result = knowledgeGraphService.resetAllData(user);
        return ResponseEntity.ok(result);
    }

    // ---- Entity resolution endpoints ----

    @PostMapping("/resolution/run")
    public ResolutionResult runResolution() {
        var user = currentUserProvider.getCurrentUser();
        return resolutionService.runResolution(user);
    }

    @GetMapping("/resolution/suggestions")
    public List<MergeSuggestion> getMergeSuggestions() {
        var user = currentUserProvider.getCurrentUser();
        return resolutionService.getSuggestions(user);
    }

    public record MergeRequest(Long primaryId, Long mergeFromId) {}
}
