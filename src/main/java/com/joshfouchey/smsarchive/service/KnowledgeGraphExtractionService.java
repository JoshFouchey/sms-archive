package com.joshfouchey.smsarchive.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshfouchey.smsarchive.dto.KgExtractionJobDto;
import com.joshfouchey.smsarchive.event.ImportCompletedEvent;
import com.joshfouchey.smsarchive.model.*;
import com.joshfouchey.smsarchive.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeGraphExtractionService {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final Set<String> VALID_ENTITY_TYPES = Set.of(
            "PERSON", "PLACE", "ORGANIZATION", "OBJECT", "EVENT",
            "CONCEPT", "FOOD", "VEHICLE", "PET", "MEDICAL", "DATE");

    private static final Map<String, String> TYPE_ALIASES = Map.of(
            "LOCATION", "PLACE", "ANIMAL", "PET", "CAR", "VEHICLE",
            "COMPANY", "ORGANIZATION", "ORG", "ORGANIZATION",
            "THING", "OBJECT", "VALUE", "CONCEPT");

    private static final String EXTRACTION_PROMPT_TEMPLATE = """
            Extract factual information from this conversation as JSON triples.
            
            Rules:
            - Only extract clearly stated facts, not opinions or questions
            - Use standardized predicates: owns, lives_in, works_at, likes, dislikes, \
            is_allergic_to, has_pet, drives, visited, birthday_is, related_to, member_of, \
            graduated_from, favorite_food, phone_number_is, email_is, hobby_is, etc.
            - Subject and object should be proper nouns or short descriptive names
            
            Types: PERSON, PLACE, ORGANIZATION, OBJECT, EVENT, CONCEPT, FOOD, VEHICLE, PET, MEDICAL, DATE
            
            Conversation:
            %s
            
            Respond with ONLY a JSON array (no other text):
            [{"subject":"name","subject_type":"PERSON","predicate":"owns","object":"Ford Mustang","object_type":"VEHICLE","confidence":0.9}]
            
            If no facts can be extracted, respond with exactly: []""";

    private final ChatModel chatModel;
    private final MessageRepository messageRepository;
    private final KgEntityRepository entityRepository;
    private final KgTripleRepository tripleRepository;
    private final KgEntityAliasRepository aliasRepository;
    private final KgExtractionJobRepository jobRepository;
    private final TaskExecutor aiTaskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final Map<UUID, Boolean> cancelledJobs = new ConcurrentHashMap<>();

    @Value("${smsarchive.ai.kg.model:phi3:3.8b}")
    private String modelName;

    @Value("${smsarchive.ai.kg.window-size:20}")
    private int windowSize;

    @Value("${smsarchive.ai.kg.min-body-length:20}")
    private int minBodyLength;

    @Value("${smsarchive.ai.kg.auto-extract:true}")
    private boolean autoExtract;

    public KnowledgeGraphExtractionService(
            ChatModel chatModel,
            MessageRepository messageRepository,
            KgEntityRepository entityRepository,
            KgTripleRepository tripleRepository,
            KgEntityAliasRepository aliasRepository,
            KgExtractionJobRepository jobRepository,
            @Qualifier("aiTaskExecutor") TaskExecutor aiTaskExecutor,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.messageRepository = messageRepository;
        this.entityRepository = entityRepository;
        this.tripleRepository = tripleRepository;
        this.aliasRepository = aliasRepository;
        this.jobRepository = jobRepository;
        this.aiTaskExecutor = aiTaskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Start KG extraction asynchronously. Returns job ID for progress tracking.
     */
    public UUID startExtraction(User user) {
        Optional<KgExtractionJob> running = jobRepository
                .findFirstByUserAndStatusOrderByCreatedAtDesc(user, "RUNNING");
        if (running.isPresent()) {
            throw new IllegalStateException("Extraction job already running: " + running.get().getId());
        }

        KgExtractionJob job = new KgExtractionJob();
        job.setUser(user);
        job.setModelName(modelName);
        job.setStatus("PENDING");
        jobRepository.save(job);

        UUID jobId = job.getId();
        aiTaskExecutor.execute(() -> {
            try {
                runExtraction(jobId, user);
            } catch (Exception e) {
                log.error("Extraction job {} failed unexpectedly", jobId, e);
            }
        });

        return jobId;
    }

    /**
     * Auto-start extraction after import completes (queues behind embedding job).
     */
    @EventListener
    public void onImportCompleted(ImportCompletedEvent event) {
        if (!autoExtract) return;

        User user = event.getUser();
        log.info("Import completed — will queue KG extraction for user {}", user.getUsername());

        try {
            startExtraction(user);
        } catch (IllegalStateException e) {
            log.debug("Skipping auto-extraction for {}: {}", user.getUsername(), e.getMessage());
        }
    }

    public void cancelJob(UUID jobId) {
        cancelledJobs.put(jobId, true);
    }

    public KgExtractionJobDto getJobStatus(UUID jobId, User user) {
        KgExtractionJob job = jobRepository.findByIdAndUser(jobId, user)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
        return toDto(job);
    }

    public List<KgExtractionJobDto> getJobHistory(User user) {
        return jobRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toDto)
                .toList();
    }

    // ---- Core extraction pipeline ----

    private void runExtraction(UUID jobId, User user) {
        KgExtractionJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now());

        try {
            List<Object[]> rows = messageRepository
                    .findUnprocessedForKgExtraction(user.getId(), minBodyLength);

            job.setTotalMessages((long) rows.size());
            jobRepository.save(job);

            if (rows.isEmpty()) {
                job.setStatus("COMPLETED");
                job.setCompletedAt(Instant.now());
                jobRepository.save(job);
                log.info("Extraction job {}: no unprocessed messages", jobId);
                return;
            }

            log.info("Extraction job {}: processing {} messages with {}",
                    jobId, rows.size(), modelName);

            // Group message IDs by conversation
            LinkedHashMap<Long, List<Long>> byConversation = new LinkedHashMap<>();
            for (Object[] row : rows) {
                Long msgId = ((Number) row[0]).longValue();
                Long convId = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                byConversation.computeIfAbsent(convId, k -> new ArrayList<>()).add(msgId);
            }

            for (var entry : byConversation.entrySet()) {
                List<Long> msgIds = entry.getValue();

                for (int i = 0; i < msgIds.size(); i += windowSize) {
                    if (Boolean.TRUE.equals(cancelledJobs.get(jobId))) {
                        job.setStatus("CANCELLED");
                        jobRepository.save(job);
                        log.info("Extraction job {} cancelled", jobId);
                        return;
                    }

                    List<Long> windowIds = msgIds.subList(
                            i, Math.min(i + windowSize, msgIds.size()));

                    try {
                        int[] results = processWindow(windowIds, user);
                        job.setProcessed(job.getProcessed() + windowIds.size());
                        job.setTriplesFound(job.getTriplesFound() + results[0]);
                        job.setEntitiesFound(job.getEntitiesFound() + results[1]);
                    } catch (Exception e) {
                        log.warn("Extraction window failed (conv {}): {}",
                                entry.getKey(), e.getMessage());
                        job.setProcessed(job.getProcessed() + windowIds.size());
                    }

                    jobRepository.save(job);
                }
            }

            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());
            log.info("Extraction job {} completed: {} triples, {} entities from {} messages",
                    jobId, job.getTriplesFound(), job.getEntitiesFound(), job.getProcessed());

        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            log.error("Extraction job {} failed", jobId, e);
        }
        jobRepository.save(job);
        cancelledJobs.remove(jobId);
    }

    /**
     * Process a window of messages: load → build prompt → call LLM → parse → persist.
     * Returns [triplesCreated, newEntitiesCreated].
     */
    private int[] processWindow(List<Long> messageIds, User user) {
        // Load messages with contacts eagerly (needed for sender names)
        List<Message> messages = messageRepository.findAllByIdWithContacts(messageIds);

        // Build conversation text for the prompt
        String conversationText = buildConversationText(messages);
        if (conversationText.isBlank()) {
            markMessagesProcessed(messageIds, user.getId());
            return new int[]{0, 0};
        }

        // Call the LLM
        String prompt = String.format(EXTRACTION_PROMPT_TEMPLATE, conversationText);
        ChatResponse response = chatModel.call(
                new Prompt(prompt, OllamaOptions.builder()
                        .model(modelName)
                        .temperature(0.1)
                        .build()));

        String llmOutput = response.getResult().getOutput().getText();
        List<ExtractedFact> facts = parseFacts(llmOutput);

        // Persist in a transaction
        int[] counts = transactionTemplate.execute(status -> {
            int triples = 0;
            int newEntities = 0;

            for (ExtractedFact fact : facts) {
                try {
                    int created = persistFact(fact, user);
                    triples++;
                    newEntities += created;
                } catch (Exception e) {
                    log.debug("Skipping fact [{} {} {}]: {}",
                            fact.subject, fact.predicate, fact.object, e.getMessage());
                }
            }

            markMessagesProcessed(messageIds, user.getId());
            return new int[]{triples, newEntities};
        });

        return counts != null ? counts : new int[]{0, 0};
    }

    // ---- Prompt building ----

    private String buildConversationText(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            if (m.getBody() == null || m.getBody().isBlank()) continue;

            String sender;
            if (m.getDirection() == MessageDirection.OUTBOUND) {
                sender = "Me";
            } else if (m.getSenderContact() != null && m.getSenderContact().getName() != null) {
                sender = m.getSenderContact().getName();
            } else {
                sender = "Unknown";
            }

            String ts = m.getTimestamp() != null ? TS_FORMAT.format(m.getTimestamp()) : "??";
            sb.append("[").append(ts).append("] ").append(sender).append(": ")
              .append(truncate(m.getBody(), 500)).append("\n");
        }
        return sb.toString();
    }

    // ---- LLM output parsing ----

    private List<ExtractedFact> parseFacts(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) return List.of();

        String json = extractJsonArray(llmOutput);
        if (json == null || json.equals("[]")) return List.of();

        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("Failed to parse LLM output as JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    // ---- Entity and triple persistence ----

    /**
     * Persist a single extracted fact. Returns the number of NEW entities created (0, 1, or 2).
     */
    private int persistFact(ExtractedFact fact, User user) {
        if (fact.subject == null || fact.predicate == null) {
            throw new IllegalArgumentException("Subject and predicate are required");
        }

        String subjectType = normalizeEntityType(fact.subjectType);
        int newEntities = 0;

        // Find or create subject
        KgEntity subject = findOrCreateEntity(user, fact.subject.trim(), subjectType);
        if (subject.getCreatedAt() != null &&
                subject.getCreatedAt().isAfter(Instant.now().minusSeconds(2))) {
            newEntities++;
        }

        // Handle object — either entity reference or literal value
        KgEntity objectEntity = null;
        String objectValue = null;

        if (fact.object != null && !fact.object.isBlank()) {
            String objectType = normalizeEntityType(fact.objectType);
            if ("CONCEPT".equals(objectType) && !VALID_ENTITY_TYPES.contains(
                    fact.objectType != null ? fact.objectType.toUpperCase() : "")) {
                // Treat as literal value
                objectValue = fact.object.trim();
            } else {
                objectEntity = findOrCreateEntity(user, fact.object.trim(), objectType);
                if (objectEntity.getCreatedAt() != null &&
                        objectEntity.getCreatedAt().isAfter(Instant.now().minusSeconds(2))) {
                    newEntities++;
                }
            }
        } else {
            objectValue = "";
        }

        // Create triple
        String predicate = normalizePredicate(fact.predicate);
        float confidence = Math.max(0.1f, Math.min(1.0f,
                fact.confidence > 0 ? fact.confidence : 0.7f));

        KgTriple triple = new KgTriple();
        triple.setUser(user);
        triple.setSubject(subject);
        triple.setPredicate(predicate);
        triple.setObject(objectEntity);
        triple.setObjectValue(objectValue);
        triple.setConfidence(confidence);
        triple.setIsVerified(false);
        triple.setIsNegated(false);
        tripleRepository.save(triple);

        return newEntities;
    }

    private KgEntity findOrCreateEntity(User user, String name, String entityType) {
        return entityRepository.findByUserAndCanonicalNameAndEntityType(user, name, entityType)
                .orElseGet(() -> {
                    KgEntity entity = new KgEntity();
                    entity.setUser(user);
                    entity.setCanonicalName(name);
                    entity.setEntityType(entityType);
                    return entityRepository.save(entity);
                });
    }

    private void markMessagesProcessed(List<Long> messageIds, UUID userId) {
        for (Long msgId : messageIds) {
            jdbcTemplate.update(
                    "INSERT INTO kg_processed_messages (message_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    msgId, userId);
        }
    }

    // ---- Normalization helpers ----

    private String normalizeEntityType(String type) {
        if (type == null || type.isBlank()) return "CONCEPT";
        String upper = type.toUpperCase().trim();
        if (VALID_ENTITY_TYPES.contains(upper)) return upper;
        return TYPE_ALIASES.getOrDefault(upper, "CONCEPT");
    }

    private String normalizePredicate(String predicate) {
        if (predicate == null) return "related_to";
        return predicate.trim().toLowerCase().replaceAll("\\s+", "_");
    }

    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    // ---- DTO mapping ----

    private KgExtractionJobDto toDto(KgExtractionJob job) {
        double pct = job.getTotalMessages() > 0
                ? (job.getProcessed() * 100.0) / job.getTotalMessages()
                : 0;
        return new KgExtractionJobDto(
                job.getId(), job.getStatus(),
                job.getTotalMessages(), job.getProcessed(),
                job.getTriplesFound(), job.getEntitiesFound(),
                pct, job.getModelName(),
                job.getStartedAt(), job.getCompletedAt(),
                job.getErrorMessage(), job.getCreatedAt());
    }

    // ---- Inner record for JSON parsing ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExtractedFact(
            String subject,
            @JsonProperty("subject_type") String subjectType,
            String predicate,
            @JsonProperty("object") String object,
            @JsonProperty("object_type") String objectType,
            float confidence
    ) {}
}
