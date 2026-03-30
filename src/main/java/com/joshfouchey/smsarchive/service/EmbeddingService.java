package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.EmbeddingJobDto;
import com.joshfouchey.smsarchive.dto.EmbeddingStatsDto;
import com.joshfouchey.smsarchive.event.ImportCompletedEvent;
import com.joshfouchey.smsarchive.exception.JobAlreadyRunningException;
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
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class EmbeddingService {

    private final OpenAiEmbeddingModel embeddingModel;
    private final MessageRepository messageRepository;
    private final MessageEmbeddingRepository embeddingRepository;
    private final EmbeddingJobRepository jobRepository;
    private final TaskExecutor aiTaskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    // Track running jobs to support cancellation
    private final Map<UUID, Boolean> cancelledJobs = new ConcurrentHashMap<>();

    @Value("${smsarchive.ai.embedding.batch-size:64}")
    private int batchSize;

    @Value("${smsarchive.ai.embedding.model:qwen3-embedding:0.6b}")
    private String modelName;

    @Value("${smsarchive.ai.embedding.max-body-chars:7500}")
    private int maxBodyChars;

    @Value("${smsarchive.ai.embedding.auto-embed:true}")
    private boolean autoEmbed;

    @Value("${smsarchive.ai.embedding.context-messages-before:3}")
    private int contextMessagesBefore;

    @Value("${smsarchive.ai.embedding.chunk-max-chars:1500}")
    private int chunkMaxChars;

    @Value("${smsarchive.ai.embedding.chunk-overlap-chars:200}")
    private int chunkOverlapChars;

    @Value("${smsarchive.ai.gpu-cooldown.enabled:false}")
    private boolean cooldownEnabled;

    @Value("${smsarchive.ai.gpu-cooldown.interval:100}")
    private int cooldownInterval;

    @Value("${smsarchive.ai.gpu-cooldown.pause-seconds:60}")
    private int cooldownPauseSeconds;

    public EmbeddingService(
            OpenAiEmbeddingModel embeddingModel,
            MessageRepository messageRepository,
            MessageEmbeddingRepository embeddingRepository,
            EmbeddingJobRepository jobRepository,
            @Qualifier("aiTaskExecutor") TaskExecutor aiTaskExecutor,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate) {
        this.embeddingModel = embeddingModel;
        this.messageRepository = messageRepository;
        this.embeddingRepository = embeddingRepository;
        this.jobRepository = jobRepository;
        this.aiTaskExecutor = aiTaskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Reset orphaned RUNNING/PENDING jobs on startup so they don't block new embeddings.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void resetOrphanedJobs() {
        List<EmbeddingJob> orphaned = jobRepository.findByStatusIn(List.of("RUNNING", "PENDING"));
        for (EmbeddingJob job : orphaned) {
            log.warn("Resetting orphaned embedding job {} (was {})", job.getId(), job.getStatus());
            job.setStatus("FAILED");
            job.setErrorMessage("Reset on startup — job was orphaned by a restart");
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
        }
        if (!orphaned.isEmpty()) {
            log.info("Reset {} orphaned embedding job(s)", orphaned.size());
        }
    }

    /**
     * Start batch embedding asynchronously. Returns job ID for progress tracking.
     * Follows the same async pattern as ImportService.
     */
    public UUID startBatchEmbedding(User user) {
        return startBatchEmbedding(user, false);
    }

    /**
     * Start a full re-embed: clears all existing embeddings and re-embeds with contextual text.
     */
    public UUID startReembedding(User user) {
        return startBatchEmbedding(user, true);
    }

    private UUID startBatchEmbedding(User user, boolean reembed) {
        // Check for already-running job
        Optional<EmbeddingJob> running = jobRepository
                .findFirstByUserAndStatusOrderByCreatedAtDesc(user, "RUNNING");
        if (running.isPresent()) {
            throw new JobAlreadyRunningException("Embedding job already running: " + running.get().getId());
        }

        if (reembed) {
            log.info("Re-embed requested for user {}. Clearing all existing embeddings.", user.getUsername());
            transactionTemplate.executeWithoutResult(status ->
                    embeddingRepository.deleteAllByUserAndModel(user.getId(), modelName));
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
            int batchCount = 0;
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

                batchCount++;
                if (cooldownEnabled && batchCount % cooldownInterval == 0) {
                    log.info("GPU cooldown: pausing {}s after {} batches ({} messages processed)",
                            cooldownPauseSeconds, batchCount, job.getProcessed());
                    try { Thread.sleep(cooldownPauseSeconds * 1000L); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
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

    private record ChunkInfo(Message message, String embeddingText, int chunkIndex, boolean isChunked) {}

    void processBatch(List<Long> messageIds, User user) {
        List<Message> messages = messageRepository.findAllByIdWithContacts(messageIds);

        // Build chunks for all messages — most will have exactly 1
        List<ChunkInfo> allChunks = new ArrayList<>();
        List<String> textsToEmbed = new ArrayList<>();

        for (Message m : messages) {
            String body = m.getBody();
            if (body == null || body.isBlank()) continue;

            List<String> bodyChunks = chunkMessage(body);
            boolean isChunked = bodyChunks.size() > 1;

            if (isChunked) {
                log.debug("Message {} ({} chars) split into {} chunks",
                        m.getId(), body.length(), bodyChunks.size());
            }

            for (int ci = 0; ci < bodyChunks.size(); ci++) {
                String contextualText = buildContextualEmbeddingText(m, bodyChunks.get(ci));
                allChunks.add(new ChunkInfo(m, contextualText, ci, isChunked));
                textsToEmbed.add(truncate(contextualText));
            }
        }

        if (textsToEmbed.isEmpty()) return;

        // Call Ollama embedding API with retry (up to 3 attempts with backoff)
        EmbeddingResponse response = callEmbeddingWithRetry(textsToEmbed);

        // Persist embeddings
        transactionTemplate.executeWithoutResult(status -> {
            // For chunked messages, delete old embeddings first to avoid stale chunks
            Set<Long> deletedIds = new HashSet<>();
            for (ChunkInfo ci : allChunks) {
                if (ci.isChunked() && deletedIds.add(ci.message().getId())) {
                    embeddingRepository.deleteByMessageIdAndModelName(
                            ci.message().getId(), modelName);
                }
            }

            for (int i = 0; i < allChunks.size(); i++) {
                ChunkInfo ci = allChunks.get(i);
                String vectorStr = toVectorString(response.getResults().get(i).getOutput());

                if (ci.isChunked()) {
                    embeddingRepository.upsertChunkEmbedding(
                            ci.message().getId(), user.getId(), vectorStr, modelName,
                            ci.embeddingText(), ci.chunkIndex(), ci.message().getId());
                } else {
                    embeddingRepository.upsertEmbedding(
                            ci.message().getId(), user.getId(), vectorStr, modelName,
                            ci.embeddingText());
                }
            }
        });
    }

    /**
     * Build a synthetic search document with conversation context.
     * Instead of embedding just "Yes", we embed:
     *   [Conversation with: Alice Smith]
     *   [Alice Smith]: Do you still live at 123 Main St?
     *   [Me]: Yes
     *
     * @param bodyOverride if non-null, use this text instead of message.getBody() (for chunks)
     */
    private String buildContextualEmbeddingText(Message message, String bodyOverride) {
        String body = bodyOverride != null ? bodyOverride : message.getBody();
        if (body == null || body.isBlank()) return "";

        if (contextMessagesBefore <= 0) {
            return body;
        }

        // Fetch preceding messages from the same conversation
        List<Map<String, Object>> contextRows = jdbcTemplate.queryForList("""
                SELECT m.body, m.direction,
                       c.name AS sender_name
                FROM messages m
                LEFT JOIN contacts c ON c.id = m.sender_contact_id
                WHERE m.conversation_id = ? AND m.timestamp < ? AND m.body IS NOT NULL
                ORDER BY m.timestamp DESC
                LIMIT ?
                """, message.getConversation() != null ? message.getConversation().getId() : -1,
                java.sql.Timestamp.from(message.getTimestamp()),
                contextMessagesBefore);

        if (contextRows.isEmpty()) {
            return body;
        }

        // Build the contextual document
        StringBuilder sb = new StringBuilder();

        // Identify the conversation, not the individual message sender.
        // Individual senders are already labeled per-message below ([Wife]:, [Me]:, etc.)
        String conversationPartner = null;
        if (message.getConversation() != null
                && message.getConversation().getName() != null && !message.getConversation().getName().isBlank()) {
            conversationPartner = message.getConversation().getName();
        }
        if (conversationPartner == null) {
            for (Map<String, Object> row : contextRows) {
                if (row.get("sender_name") != null && "INBOUND".equals(String.valueOf(row.get("direction")))) {
                    conversationPartner = String.valueOf(row.get("sender_name"));
                    break;
                }
            }
        }
        if (conversationPartner != null) {
            sb.append("[Conversation with: ").append(conversationPartner).append("]\n");
        }

        // Add context messages (reversed to chronological order)
        for (int i = contextRows.size() - 1; i >= 0; i--) {
            Map<String, Object> row = contextRows.get(i);
            String sender = "OUTBOUND".equals(String.valueOf(row.get("direction")))
                    ? "Me"
                    : (row.get("sender_name") != null ? String.valueOf(row.get("sender_name")) : "Them");
            String ctxBody = String.valueOf(row.get("body"));
            // Keep context messages short
            if (ctxBody.length() > 200) ctxBody = ctxBody.substring(0, 200) + "...";
            sb.append("[").append(sender).append("]: ").append(ctxBody).append("\n");
        }

        // Add the target message — use actual sender contact name for INBOUND
        String targetSender;
        if (message.getDirection() != null && "OUTBOUND".equals(message.getDirection().name())) {
            targetSender = "Me";
        } else if (message.getSenderContact() != null && message.getSenderContact().getName() != null) {
            targetSender = message.getSenderContact().getName();
        } else {
            targetSender = conversationPartner != null ? conversationPartner : "Them";
        }
        sb.append("[").append(targetSender).append("]: ").append(body);

        return sb.toString();
    }

    private EmbeddingResponse callEmbeddingWithRetry(List<String> texts) {
        Exception lastException = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return embeddingModel.call(
                        new EmbeddingRequest(texts, OpenAiEmbeddingOptions.builder()
                                .model(modelName)
                                .encodingFormat("float")
                                .build()));
            } catch (Exception e) {
                lastException = e;
                long delay = 1000L * (1 << attempt); // 1s, 2s, 4s
                log.warn("Embedding attempt {} failed (retrying in {}ms): {}",
                        attempt + 1, delay, e.getMessage());
                try { Thread.sleep(delay); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
        throw new RuntimeException("Embedding failed after 3 attempts", lastException);
    }

    /**
     * Embed a single query string for search.
     */
    public float[] embedQuery(String query) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(
                        List.of(truncate(query)),
                        OpenAiEmbeddingOptions.builder().model(modelName).encodingFormat("float").build()));
        return response.getResults().get(0).getOutput();
    }

    public void cancelJob(UUID jobId) {
        cancelledJobs.put(jobId, true);
    }

    /**
     * Auto-embed new messages after an import completes.
     * Triggered by ImportCompletedEvent published from ImportService.
     */
    @EventListener
    public void onImportCompleted(ImportCompletedEvent event) {
        if (!autoEmbed) return;

        User user = event.getUser();
        log.info("Import completed for user {} ({} messages). Checking for unembedded messages...",
                user.getUsername(), event.getImportedCount());

        // Skip if a job is already running for this user
        Optional<EmbeddingJob> running = jobRepository
                .findFirstByUserAndStatusOrderByCreatedAtDesc(user, "RUNNING");
        if (running.isPresent()) {
            log.info("Embedding job already running for {}. New messages will be picked up.", user.getUsername());
            return;
        }

        try {
            UUID jobId = startBatchEmbedding(user);
            log.info("Auto-started embedding job {} for user {}", jobId, user.getUsername());
        } catch (IllegalStateException e) {
            log.debug("Skipping auto-embed for {}: {}", user.getUsername(), e.getMessage());
        }
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

    /**
     * Split a long message body into semantic chunks with overlap.
     * Short messages return as a single chunk. Long messages are split on
     * paragraph breaks, sentence boundaries, or newlines — never mid-word.
     */
    List<String> chunkMessage(String body) {
        if (body == null || body.isBlank()) return List.of("");
        if (body.length() <= chunkMaxChars) return List.of(body);

        List<String> chunks = new ArrayList<>();
        int pos = 0;

        while (pos < body.length()) {
            int end = Math.min(pos + chunkMaxChars, body.length());

            if (end < body.length()) {
                int breakAt = findBreakPoint(body, pos, end);
                if (breakAt > pos) end = breakAt;
            }

            String chunk = body.substring(pos, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);

            // Advance with overlap, but never go backwards
            int nextPos = end - chunkOverlapChars;
            if (nextPos <= pos) nextPos = end;
            pos = nextPos;
        }

        return chunks.isEmpty() ? List.of(body) : chunks;
    }

    /**
     * Find the best semantic break point searching backwards from {@code end}.
     * Priority: paragraph break → sentence end → newline → space → hard cut.
     */
    private int findBreakPoint(String text, int start, int end) {
        int minPos = start + chunkMaxChars / 4; // don't break too early

        // 1. Paragraph break (\n\n)
        int idx = text.lastIndexOf("\n\n", end);
        if (idx >= minPos) return idx + 2;

        // 2. Sentence-ending punctuation followed by space or newline
        for (int i = end - 1; i >= minPos; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && i + 1 < text.length()
                    && (text.charAt(i + 1) == ' ' || text.charAt(i + 1) == '\n')) {
                return i + 1;
            }
        }

        // 3. Single newline
        idx = text.lastIndexOf('\n', end - 1);
        if (idx >= minPos) return idx + 1;

        // 4. Space (don't break mid-word)
        idx = text.lastIndexOf(' ', end - 1);
        if (idx >= minPos) return idx + 1;

        // 5. Hard cut at max chars
        return end;
    }

    private String truncate(String text) {
        if (text == null) return "";
        // Strip unpaired surrogates that break JSON serialization for llama.cpp
        text = text.replaceAll("[\\uD800-\\uDBFF](?![\\uDC00-\\uDFFF])", "")
                   .replaceAll("(?<![\\uD800-\\uDBFF])[\\uDC00-\\uDFFF]", "");
        return text.length() > maxBodyChars ? text.substring(0, maxBodyChars) : text;
    }
}
