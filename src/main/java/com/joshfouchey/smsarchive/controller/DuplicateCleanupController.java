package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.service.DuplicateCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for managing duplicate message cleanup.
 * This is a utility endpoint for cleaning up duplicates that were imported before the fix.
 */
@RestController
@RequestMapping("/api/admin/cleanup")
@Slf4j
public class DuplicateCleanupController {

    private final DuplicateCleanupService cleanupService;

    public DuplicateCleanupController(DuplicateCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    /**
     * Preview duplicate messages without deleting them.
     * GET /api/admin/cleanup/duplicates/preview
     *
     * @return Statistics about duplicates and sample groups
     */
    @GetMapping("/duplicates/preview")
    public ResponseEntity<Map<String, Object>> previewDuplicates() {
        log.info("Preview duplicates requested");
        Map<String, Object> preview = cleanupService.previewDuplicates();
        return ResponseEntity.ok(preview);
    }

    /**
     * Remove duplicate messages, keeping only the first occurrence.
     * DELETE /api/admin/cleanup/duplicates
     *
     * @return Statistics about the cleanup operation
     */
    @DeleteMapping("/duplicates")
    public ResponseEntity<Map<String, Object>> removeDuplicates() {
        log.info("Remove duplicates requested");
        Map<String, Object> stats = cleanupService.removeDuplicates();
        return ResponseEntity.ok(stats);
    }
}

