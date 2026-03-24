package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.KgEntityDto;
import com.joshfouchey.smsarchive.dto.KgTripleDto;
import com.joshfouchey.smsarchive.dto.KnowledgeGraphDto;
import com.joshfouchey.smsarchive.service.CurrentUserProvider;
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

@RestController
@RequestMapping("/api/knowledge-graph")
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;
    private final CurrentUserProvider currentUserProvider;

    public KnowledgeGraphController(KnowledgeGraphService knowledgeGraphService,
                                    CurrentUserProvider currentUserProvider) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/entities")
    public List<KgEntityDto> getEntities(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search) {
        var user = currentUserProvider.getCurrentUser();
        return knowledgeGraphService.getEntities(user, type, search);
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

    public record MergeRequest(Long primaryId, Long mergeFromId) {}
}
