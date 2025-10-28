package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for rebuilding thumbnails for existing media parts in the database.
 * Provides async job processing with progress tracking.
 */
@Slf4j
@Service
public class ThumbnailRebuildJobService {

    private final MessagePartRepository partRepo;
    private final CurrentUserProvider currentUserProvider;
    private final ThumbnailService thumbnailService;
    private TaskExecutor taskExecutor;

    private final Map<UUID, ThumbnailJobProgress> jobs = new ConcurrentHashMap<>();

    public ThumbnailRebuildJobService(
            MessagePartRepository partRepo,
            CurrentUserProvider currentUserProvider,
            ThumbnailService thumbnailService) {
        this.partRepo = partRepo;
        this.currentUserProvider = currentUserProvider;
        this.thumbnailService = thumbnailService;
    }

    @Autowired(required = false)
    public void setTaskExecutor(@Qualifier("importTaskExecutor") TaskExecutor executor) {
        this.taskExecutor = executor;
    }

    /**
     * Start a thumbnail rebuild job.
     *
     * @param contactId  Optional contact ID to filter parts
     * @param force      If true, regenerate existing thumbnails
     * @param batchSize  Number of parts to process (currently informational)
     * @param async      If true, run in background; if false, run synchronously
     * @return Progress object for tracking job status
     */
    public ThumbnailJobProgress startJob(Long contactId, boolean force, int batchSize, boolean async) {
        ThumbnailJobProgress progress = new ThumbnailJobProgress();
        jobs.put(progress.getId(), progress);

        // Capture current user for async execution
        User currentUser = currentUserProvider.getCurrentUser();

        Runnable task = () -> execute(progress, currentUser, contactId, force);

        if (async) {
            if (taskExecutor != null) {
                taskExecutor.execute(task);
                log.info("Started async thumbnail rebuild job: {}", progress.getId());
            } else {
                // Fallback to new thread
                Thread thread = new Thread(task, "thumb-rebuild-" + progress.getId());
                thread.setDaemon(true);
                thread.start();
                log.info("Started thumbnail rebuild job in fallback thread: {}", progress.getId());
            }
        } else {
            log.info("Running thumbnail rebuild job synchronously: {}", progress.getId());
            task.run();
        }

        return progress;
    }

    /**
     * Get progress for a specific job.
     *
     * @param jobId Job ID
     * @return Progress object, or null if job not found
     */
    public ThumbnailJobProgress getJob(UUID jobId) {
        return jobs.get(jobId);
    }

    /**
     * Execute the thumbnail rebuild job.
     */
    private void execute(ThumbnailJobProgress progress, User user, Long contactId, boolean force) {
        progress.start();

        try {
            // Fetch image parts from database
            List<MessagePart> parts;
            if (contactId != null) {
                log.info("Fetching image parts for contact ID: {}", contactId);
                parts = partRepo.findImagePartsByContactId(contactId, user);
            } else {
                log.info("Fetching all image parts for user: {}", user.getUsername());
                parts = partRepo.findAllImagePartsByUser(user);
            }

            progress.setTotal(parts.size());
            log.info("Processing {} image parts (force={})", parts.size(), force);

            // Process each part
            for (MessagePart part : parts) {
                try {
                    processMessagePart(part, force, progress);
                } catch (Exception e) {
                    String error = String.format("partId=%d: %s", part.getId(), e.getMessage());
                    progress.addError(error);
                    log.warn("Error processing part {}: {}", part.getId(), e.getMessage());
                } finally {
                    progress.incProcessed();
                }
            }

            progress.finish("COMPLETED");
            log.info("Thumbnail rebuild job {} completed: regenerated={}, skipped={}, errors={}",
                    progress.getId(), progress.getRegeneratedThumbnails(),
                    progress.getSkippedThumbnails(), progress.getErrors().size());

        } catch (Exception e) {
            log.error("Thumbnail rebuild job {} failed", progress.getId(), e);
            progress.finish("FAILED");
            progress.addError("Job failed: " + e.getMessage());
        }
    }

    /**
     * Process a single message part for thumbnail generation.
     */
    private void processMessagePart(MessagePart part, boolean force, ThumbnailJobProgress progress) {
        String filePath = part.getFilePath();

        // Skip if no file path
        if (filePath == null || filePath.isBlank()) {
            log.debug("Skipping part {} - no file path", part.getId());
            progress.incSkipped();
            return;
        }

        try {
            Path originalPath = Paths.get(filePath);

            // Check if original file exists
            if (!Files.exists(originalPath)) {
                String error = String.format("partId=%d: Original file not found: %s", part.getId(), filePath);
                progress.addError(error);
                log.warn("Original file not found for part {}: {}", part.getId(), filePath);
                progress.incSkipped();
                return;
            }

            // Check if file is readable
            if (!Files.isReadable(originalPath)) {
                String error = String.format("partId=%d: Original file not readable: %s", part.getId(), filePath);
                progress.addError(error);
                log.warn("Original file not readable for part {}: {}", part.getId(), filePath);
                progress.incSkipped();
                return;
            }

            // Derive thumbnail path
            Path thumbPath = thumbnailService.deriveThumbnailPath(originalPath, part.getSeq());

            // Attempt to create thumbnail
            boolean created = thumbnailService.createThumbnail(
                    originalPath,
                    thumbPath,
                    part.getContentType(),
                    force
            );

            if (created) {
                progress.incRegenerated();
                log.debug("Successfully processed part {}: {}", part.getId(), thumbPath);
            } else {
                progress.incSkipped();
                log.debug("Skipped part {} (not a supported image type or already exists)", part.getId());
            }

        } catch (Exception e) {
            String error = String.format("partId=%d: %s", part.getId(), e.getMessage());
            progress.addError(error);
            log.warn("Error processing part {}: {}", part.getId(), e.getMessage());
            progress.incSkipped();
        }
    }
}

