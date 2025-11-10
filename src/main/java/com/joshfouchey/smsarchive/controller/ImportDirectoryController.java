package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.service.ImportDirectoryWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for managing the import directory watcher.
 * Only available when the import directory feature is enabled.
 */
@RestController
@RequestMapping("/api/import-directory")
@ConditionalOnBean(ImportDirectoryWatcher.class)
public class ImportDirectoryController {

    private final ImportDirectoryWatcher watcher;

    @Autowired
    public ImportDirectoryController(ImportDirectoryWatcher watcher) {
        this.watcher = watcher;
    }

    /**
     * Get the current status of the import directory watcher
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
            "enabled", watcher.isScanningEnabled(),
            "status", watcher.getStatus()
        ));
    }

    /**
     * Pause the import directory scanner
     */
    @PostMapping("/pause")
    public ResponseEntity<Map<String, String>> pause() {
        watcher.pauseScanning();
        return ResponseEntity.ok(Map.of(
            "message", "Import directory scanning paused",
            "status", "paused"
        ));
    }

    /**
     * Resume the import directory scanner
     */
    @PostMapping("/resume")
    public ResponseEntity<Map<String, String>> resume() {
        watcher.resumeScanning();
        return ResponseEntity.ok(Map.of(
            "message", "Import directory scanning resumed",
            "status", "enabled"
        ));
    }

    /**
     * Trigger an immediate scan (doesn't wait for next scheduled interval)
     */
    @PostMapping("/scan-now")
    public ResponseEntity<Map<String, String>> scanNow() {
        if (!watcher.isScanningEnabled()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Scanning is currently paused"
            ));
        }

        // Trigger scan in a separate thread so we can return immediately
        new Thread(() -> watcher.scanImportDirectory(), "manual-scan").start();

        return ResponseEntity.ok(Map.of(
            "message", "Manual scan triggered",
            "status", "scanning"
        ));
    }
}

