package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.service.ThumbnailJobProgress;
import com.joshfouchey.smsarchive.service.ThumbnailRebuildJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for managing media job operations like thumbnail rebuilds.
 */
@RestController
@RequestMapping("/api/media/jobs")
public class MediaJobController {

    private final ThumbnailRebuildJobService jobService;

    public MediaJobController(ThumbnailRebuildJobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Start a thumbnail rebuild job.
     *
     * @param contactId  Optional contact ID to filter parts
     * @param force      If true, regenerate existing thumbnails (default: false)
     * @param batchSize  Batch size hint (default: 200, currently informational)
     * @param async      If true, run asynchronously (default: true)
     * @return Job ID and initial status
     */
    @PostMapping("/rebuild-thumbnails")
    public ResponseEntity<?> rebuildThumbnails(
            @RequestParam(required = false) Long contactId,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(defaultValue = "200") int batchSize,
            @RequestParam(defaultValue = "true") boolean async) {

        ThumbnailJobProgress progress = jobService.startJob(contactId, force, batchSize, async);

        return ResponseEntity.ok(Map.of(
                "jobId", progress.getId(),
                "status", progress.getStatus()
        ));
    }

    /**
     * Get the status and progress of a thumbnail rebuild job.
     *
     * @param id Job ID
     * @return Job progress details
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getJobStatus(@PathVariable UUID id) {
        ThumbnailJobProgress progress = jobService.getJob(id);

        if (progress == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Not Found",
                    "message", "Job not found: " + id
            ));
        }

        // Use HashMap to avoid Map.of's 10-entry limit
        var response = new java.util.HashMap<String, Object>();
        response.put("jobId", progress.getId());
        response.put("status", progress.getStatus());
        response.put("percentComplete", progress.getPercentComplete());
        response.put("totalParts", progress.getTotalParts());
        response.put("processedParts", progress.getProcessedParts());
        response.put("regeneratedThumbnails", progress.getRegeneratedThumbnails());
        response.put("skippedThumbnails", progress.getSkippedThumbnails());
        response.put("errorCount", progress.getErrors().size());
        response.put("errors", progress.getErrors());
        response.put("startedAt", progress.getStartedAt());
        response.put("finishedAt", progress.getFinishedAt());

        return ResponseEntity.ok(response);
    }
}

