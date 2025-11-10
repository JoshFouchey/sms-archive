package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches a configured directory for XML files and automatically imports them.
 * This allows users to drop large XML files directly on the server file system,
 * bypassing upload size limits from reverse proxies or web servers.
 *
 * Directory structure: import-drop/username/*.xml
 * Each user has their own subdirectory named by their username.
 *
 * Features:
 * - Configurable scan interval (default: 5 minutes)
 * - Auto-delete or retain processed files
 * - Automatic cleanup of old processed files
 * - File age threshold before processing
 * - Can be paused/resumed via API
 * - User isolation - each user's files go to their own account
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "smsarchive.import.directory.enabled", havingValue = "true")
public class ImportDirectoryWatcher {

    private final ImportService importService;
    private final UserRepository userRepository;

    @Value("${smsarchive.import.directory.path:./import-drop}")
    private String importDirectoryPath;

    @Value("${smsarchive.import.directory.scanIntervalSeconds:300}")
    private long scanIntervalSeconds;

    @Value("${smsarchive.import.directory.deleteAfterImport:false}")
    private boolean deleteAfterImport;

    @Value("${smsarchive.import.directory.retentionDays:7}")
    private int retentionDays;

    @Value("${smsarchive.import.directory.fileAgeThresholdSeconds:30}")
    private long fileAgeThresholdSeconds;

    // Track files currently being processed to avoid duplicate processing
    private final Set<String> processingFiles = ConcurrentHashMap.newKeySet();

    // Allow pausing/resuming the scanner
    private final AtomicBoolean scanningEnabled = new AtomicBoolean(true);

    public ImportDirectoryWatcher(ImportService importService, UserRepository userRepository) {
        this.importService = importService;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        try {
            Path importDir = Paths.get(importDirectoryPath);
            if (!Files.exists(importDir)) {
                Files.createDirectories(importDir);
                log.info("Created import drop directory: {}", importDir.toAbsolutePath());
            }

            // Create processed subdirectory if not deleting files
            if (!deleteAfterImport) {
                Path processedDir = importDir.resolve("processed");
                if (!Files.exists(processedDir)) {
                    Files.createDirectories(processedDir);
                }
            }

            log.info("Import directory watcher initialized:");
            log.info("  - Drop directory: {}", importDir.toAbsolutePath());
            log.info("  - Structure: Each user has subdirectory named by username");
            log.info("  - Example: {}/your-username/*.xml", importDir.toAbsolutePath());
            log.info("  - Scan interval: {} seconds ({} minutes)", scanIntervalSeconds, scanIntervalSeconds / 60);
            log.info("  - File age threshold: {} seconds", fileAgeThresholdSeconds);
            log.info("  - Delete after import: {}", deleteAfterImport);
            if (!deleteAfterImport) {
                log.info("  - Retention period: {} days", retentionDays);
            }
        } catch (IOException e) {
            log.error("Failed to initialize import directory: {}", importDirectoryPath, e);
        }
    }

    /**
     * Scans the import directory for new XML files based on configured interval.
     * Expects directory structure: import-drop/username/*.xml
     */
    @Scheduled(fixedDelayString = "${smsarchive.import.directory.scanIntervalSeconds:300}000", initialDelay = 30000)
    public void scanImportDirectory() {
        if (!scanningEnabled.get()) {
            log.debug("Import directory scanning is paused");
            return;
        }

        Path importDir = Paths.get(importDirectoryPath);

        if (!Files.exists(importDir)) {
            return;
        }

        int totalFilesFound = 0;
        int totalFilesProcessed = 0;

        // Scan for username subdirectories
        try (DirectoryStream<Path> userDirs = Files.newDirectoryStream(importDir, Files::isDirectory)) {
            for (Path userDir : userDirs) {
                String username = userDir.getFileName().toString();

                // Skip processed directory
                if ("processed".equals(username)) {
                    continue;
                }

                // Verify user exists
                Optional<User> userOpt = userRepository.findByUsername(username);
                if (userOpt.isEmpty()) {
                    log.warn("Skipping directory '{}' - user '{}' not found in database", userDir.getFileName(), username);
                    continue;
                }

                // Scan for XML files in user's directory
                try (DirectoryStream<Path> xmlFiles = Files.newDirectoryStream(userDir, "*.xml")) {
                    for (Path xmlFile : xmlFiles) {
                        totalFilesFound++;

                        String fileName = xmlFile.getFileName().toString();
                        String fileKey = username + "/" + fileName;

                        if (processingFiles.contains(fileKey)) {
                            log.debug("File {} is already being processed, skipping", fileKey);
                            continue;
                        }

                        if (!isFileOldEnough(xmlFile)) {
                            log.debug("File {} is too recent (< {} seconds old), skipping",
                                    fileKey, fileAgeThresholdSeconds);
                            continue;
                        }

                        processFile(xmlFile, username);
                        totalFilesProcessed++;
                    }
                } catch (IOException e) {
                    log.error("Error scanning user directory: {}", username, e);
                }
            }
        } catch (IOException e) {
            log.error("Error scanning import directory", e);
        }

        if (totalFilesFound > 0) {
            log.info("Scan complete: found {} XML file(s), processed {}", totalFilesFound, totalFilesProcessed);
        }
    }

    /**
     * Periodically cleans up old processed files based on retention period
     * Runs once per day at 3 AM
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldProcessedFiles() {
        if (deleteAfterImport || retentionDays <= 0) {
            return; // No cleanup needed
        }

        Path importDir = Paths.get(importDirectoryPath);
        if (!Files.exists(importDir)) {
            return;
        }

        Instant cutoffTime = Instant.now().minus(Duration.ofDays(retentionDays));
        int deletedCount = 0;

        // Clean up each user's processed directory
        try (DirectoryStream<Path> userDirs = Files.newDirectoryStream(importDir, Files::isDirectory)) {
            for (Path userDir : userDirs) {
                Path processedDir = userDir.resolve("processed");
                if (!Files.exists(processedDir)) {
                    continue;
                }

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(processedDir, "*.xml")) {
                    for (Path file : stream) {
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                            Instant lastModified = attrs.lastModifiedTime().toInstant();

                            if (lastModified.isBefore(cutoffTime)) {
                                Files.delete(file);
                                deletedCount++;
                                log.debug("Deleted old processed file: {}", file.getFileName());
                            }
                        } catch (IOException e) {
                            log.warn("Failed to delete old file: {}", file.getFileName(), e);
                        }
                    }
                } catch (IOException e) {
                    log.error("Error during cleanup of processed files in: {}", processedDir, e);
                }
            }
        } catch (IOException e) {
            log.error("Error during cleanup of import directory", e);
        }

        if (deletedCount > 0) {
            log.info("Cleanup complete: deleted {} processed file(s) older than {} days",
                    deletedCount, retentionDays);
        }
    }

    /**
     * Check if file is old enough to process (created at least X seconds ago)
     * This is more reliable than checking size stability
     */
    private boolean isFileOldEnough(Path file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            Instant creationTime = attrs.creationTime().toInstant();
            Instant threshold = Instant.now().minus(Duration.ofSeconds(fileAgeThresholdSeconds));
            return creationTime.isBefore(threshold);
        } catch (IOException e) {
            log.warn("Could not read file attributes for {}, assuming not ready", file.getFileName());
            return false;
        }
    }

    private void processFile(Path xmlFile, String username) {
        String fileName = xmlFile.getFileName().toString();
        String fileKey = username + "/" + fileName;
        processingFiles.add(fileKey);

        try {
            log.info("Starting automatic import of file: {} for user: {} (size: {} MB)",
                    xmlFile.toAbsolutePath(),
                    username,
                    Files.size(xmlFile) / 1024 / 1024);

            // Start async import with username
            var jobId = importService.startImportAsyncForUser(xmlFile, username);

            log.info("Import job {} started for user: {}, file: {}", jobId, username, fileName);

            if (deleteAfterImport) {
                // Delete the file after import starts
                Files.delete(xmlFile);
                log.info("Deleted {} after starting import for user: {}", fileName, username);
            } else {
                // Move to user's processed subdirectory
                Path processedDir = xmlFile.getParent().resolve("processed");
                Files.createDirectories(processedDir);
                Path targetPath = processedDir.resolve(fileName);

                // If file already exists in processed, add timestamp
                if (Files.exists(targetPath)) {
                    String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
                    String ext = fileName.substring(fileName.lastIndexOf('.'));
                    targetPath = processedDir.resolve(nameWithoutExt + "_" + System.currentTimeMillis() + ext);
                }

                Files.move(xmlFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Moved {} to processed directory for user: {}", fileName, username);
            }

        } catch (Exception e) {
            log.error("Failed to process import file: {} for user: {}", fileName, username, e);
        } finally {
            processingFiles.remove(fileKey);
        }
    }

    // Public API methods for controlling the watcher

    public void pauseScanning() {
        scanningEnabled.set(false);
        log.info("Import directory scanning paused");
    }

    public void resumeScanning() {
        scanningEnabled.set(true);
        log.info("Import directory scanning resumed");
    }

    public boolean isScanningEnabled() {
        return scanningEnabled.get();
    }

    public String getStatus() {
        return String.format(
            "Scanning: %s, Directory: %s, Interval: %ds, Files in progress: %d",
            scanningEnabled.get() ? "enabled" : "paused",
            importDirectoryPath,
            scanIntervalSeconds,
            processingFiles.size()
        );
    }
}

