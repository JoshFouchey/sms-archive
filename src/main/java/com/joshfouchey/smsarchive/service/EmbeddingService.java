package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.EmbeddingJobDto;
import com.joshfouchey.smsarchive.dto.EmbeddingStatsDto;
import com.joshfouchey.smsarchive.model.EmbeddingJob;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessageEmbedding;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.EmbeddingJobRepository;
import com.joshfouchey.smsarchive.repository.MessageEmbeddingRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class EmbeddingService {

    private final OllamaEmbeddingModel embeddingModel;
    private final MessageRepository messageRepository;
    private final MessageEmbeddingRepository embeddingRepository;
    private final EmbeddingJobRepository jobRepository;
    private final TaskExecutor aiTaskExecutor;

    // Track running jobs to support cancellation
    private final Map<UUID, Boolean> cancelledJobs = new ConcurrentHashMap<>();

    @Value("${smsarchive.ai.embedding.batch-size:64}")
    private int batchSize;

    @Value("${smsarchive.ai.embedding.model:nomic-embed-text}")
    private String modelName;

    @Value("${smsarchive.ai.embedding.max-body-chars:7500}")
    private int maxBodyChars;

    public EmbeddingService(
            OllamaEmbeddingModel embeddingModel,
            MessageRepository messageRepository,
            MessageEmbeddingRepository embeddingRepository,
            EmbeddingJobRepository jobRepository,
            @Qualifier("aiTaskExecutor") TaskExecutor aiTaskExecutor) {
        this.embeddingModel = embeddingModel;
        this.messageRepository = messageRepository;
        this.embeddingRepository = embeddingRepository;
        this.jobRepository = jobRepository;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    /**
     * Start batch embedding asynchronously. Returns job ID for progress tracking.
     * Follows the same async pattern as ImportService.
     */
    public UUID startBatchEmbedding(User user) {
        // Check for already-running job
        Optional<EmbeddingJob> running = jobRepository
                .findFirstByUserAndStatusOrderByCreatedAtDesc(user, "RUNNING");
        if (running.isPresent()) {
            throw new IllegalStateException("Embedding job already running: " + running.get().getId());
        }

        EmbeddingJob job = new EmbeddingJob();
        job.setUser(user);
        job.setModelName(modelName);
        job.setStatus("PENDING");
        jobRepository.save(job);

        UUID jobId = job.getId();

        // Capture user before leaving request thread
        aiTaskExecutor.execute(() -> {
            try {
                runBatchEmbedding(jobId, user);
            } catch (Exception e) {
                log.error("Embedding job {} failed unexpectedly", jobId, e);
            }
        });

        return jobId;
    }

    private void runBatchEmbedding(UUID jobId, User user) {
        EmbeddingJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now());
        jobRepository.save(job);

        try {
            List<Long> unembeddedIds = embeddingRepository
                    .findUnembeddedMessageIds(user.getId(), modelName);

            job.setTotalMessages((long) unembeddedIds.size());
            jobRepository.save(job);

            log.info("Embedding job {}: processing {} messages with model {}",
                    jobId, unembeddedIds.size(), modelName);

            // Process in batches
            for (int i = 0; i < unembeddedIds.size(); i += batchSize) {
                if (Boolean.TRUE.equals(cancelledJobs.get(jobId))) {
                    job.setStatus("CANCELLED");
                    jobRepository.save(job);
                    log.info("Embedding job {} cancelled at message {}", jobId, job.getProcessed());
                    return;
                }

                List<Long> batchIds = unembeddedIds.subList(
                        i, Math.min(i + batchSize, unembeddedIds.size()));

                try {
                    processBatch(batchIds, user);
                    job.setProcessed(job.getProcessed() + batchIds.size());
                } catch (Exception e) {
                    log.warn("Embedding batch failed (messages {}-{}): {}",
                            batchIds.get(0), batchIds.get(batchIds.size() - 1), e.getMessage());
                    job.setFailed(job.getFailed() + batchIds.size());
                }
                jobRepository.save(job);
            }

            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());
            log.info("Embedding job {} completed: {} processed, {} failed",
                    jobId, job.getProcessed(), job.getFailed());

        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            log.error("Embedding job {} failed", jobId, e);
        }
        jobRepository.save(job);
        cancelledJobs.remove(jobId);
    }

    @Transactional
    void processBatch(List<Long> messageIds, User user) {
        List<Message> messages = messageRepository.findAllById(messageIds);

        // Prepare texts with nomic-embed-text task prefix
        List<String> texts = messages.stream()
                .map(m -> "search_document: " + truncate(m.getBody()))
                .toList();

        // Call Ollama embedding API
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(texts, OllamaOptions.builder()
                        .model(modelName)
                        .build()));

        // Persist embeddings via native INSERT (avoids Hibernate varchar→vector type mismatch)
        for (int i = 0; i < messages.size(); i++) {
            String vectorStr = toVectorString(response.getResults().get(i).getOutput());
            embeddingRepository.insertEmbedding(
                    messages.get(i).getId(),
                    user.getId(),
                    vectorStr,
                    modelName);
        }
    }

    /**
     * Embed a single query string for search.
     * Uses "search_query: " prefix per nomic-embed-text spec.
     */
    public float[] embedQuery(String query) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(
                        List.of("search_query: " + query),
                        OllamaOptions.builder().model(modelName).build()));
        return response.getResults().get(0).getOutput();
    }

    public void cancelJob(UUID jobId) {
        cancelledJobs.put(jobId, true);
    }

    public EmbeddingJobDto getJobStatus(UUID jobId, User user) {
        EmbeddingJob job = jobRepository.findByIdAndUser(jobId, user)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
        return toDto(job);
    }

    public List<EmbeddingJobDto> getJobHistory(User user) {
        return jobRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toDto)
                .toList();
    }

    public EmbeddingStatsDto getStats(User user) {
        long total = messageRepository.countByUser(user);
        long embedded = embeddingRepository.countByUserAndModelName(user, modelName);
        double pct = total > 0 ? (embedded * 100.0) / total : 0;
        return new EmbeddingStatsDto(total, embedded, pct, modelName);
    }

    private EmbeddingJobDto toDto(EmbeddingJob job) {
        double pct = job.getTotalMessages() > 0
                ? (job.getProcessed() * 100.0) / job.getTotalMessages()
                : 0;
        return new EmbeddingJobDto(
                job.getId(), job.getStatus(),
                job.getTotalMessages(), job.getProcessed(), job.getFailed(),
                job.getModelName(), pct,
                job.getStartedAt(), job.getCompletedAt(),
                job.getErrorMessage(), job.getCreatedAt());
    }

    /** Convert float[] to PostgreSQL vector literal "[0.1,0.2,...]" */
    static String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /** Convert double[] to PostgreSQL vector literal */
    public static String toVectorString(double[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append((float) vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > maxBodyChars ? text.substring(0, maxBodyChars) : text;
    }
}
