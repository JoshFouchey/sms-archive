package com.joshfouchey.smsarchive.service;

import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Progress tracking object for thumbnail rebuild jobs.
 * Thread-safe for concurrent updates.
 */
@Getter
public class ThumbnailJobProgress {
    private final UUID id = UUID.randomUUID();
    private volatile String status = "PENDING";
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private final AtomicInteger totalParts = new AtomicInteger(0);
    private final AtomicInteger processedParts = new AtomicInteger(0);
    private final AtomicInteger regenerated = new AtomicInteger(0);
    private final AtomicInteger skipped = new AtomicInteger(0);
    private final List<String> errors = new CopyOnWriteArrayList<>();

    // Getters using atomic/volatile reads
    public UUID getId() { return id; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getTotalParts() { return totalParts.get(); }
    public int getProcessedParts() { return processedParts.get(); }
    public int getRegeneratedThumbnails() { return regenerated.get(); }
    public int getSkippedThumbnails() { return skipped.get(); }
    public List<String> getErrors() { return List.copyOf(errors); }

    public double getPercentComplete() {
        int total = getTotalParts();
        if (total == 0) return 0.0;
        return Math.min(100.0, (processedParts.get() * 100.0) / total);
    }

    // Package-private setters for job service
    void start() {
        status = "RUNNING";
        startedAt = Instant.now();
    }

    void finish(String finalStatus) {
        status = finalStatus;
        finishedAt = Instant.now();
    }

    void setTotal(int total) {
        totalParts.set(total);
    }

    void incProcessed() {
        processedParts.incrementAndGet();
    }

    void incRegenerated() {
        regenerated.incrementAndGet();
    }

    void incSkipped() {
        skipped.incrementAndGet();
    }

    void addError(String error) {
        errors.add(error);
    }
}

