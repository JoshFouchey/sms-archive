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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
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
import java.util.concurrent.*;

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

    private static final Set<String> CANONICAL_PREDICATES = Set.of(
            "owns", "lives_in", "works_at", "works_as", "likes", "dislikes",
            "is_allergic_to", "has_pet", "drives", "visited", "birthday_is",
            "related_to", "member_of", "graduated_from", "studies_at",
            "favorite_food", "phone_number_is", "email_is", "hobby_is",
            "married_to", "engaged_to", "dating", "sibling_of", "parent_of", "child_of",
            "friend_of", "neighbor_of", "plays", "watches", "diagnosed_with",
            "takes_medication", "born_in", "moved_to", "traveled_to",
            "bought", "sold", "broke", "lost", "found", "wants", "plans_to",
            "nickname_is", "age_is", "prefers", "attended", "celebrated",
            "proposed_to", "expecting", "adopted", "coaches", "teaches",
            "manages", "retired_from", "serves_in", "volunteers_at");

    private static final Set<String> REJECTED_ENTITY_NAMES = Set.of(
            "he", "she", "it", "they", "them", "him", "her", "his",
            "we", "us", "our", "i", "my", "you", "your", "someone",
            "something", "anyone", "anything", "everyone", "everything",
            "nobody", "nothing", "that", "this", "those", "these",
            "the", "a", "an", "some", "other", "one", "here", "there");

    private static final String EXTRACTION_PROMPT_TEMPLATE = """
            You are a fact extractor for a personal knowledge graph. Extract facts from text messages as JSON.
            
            %s
            
            RULES:
            1. The SPEAKER sent the message. The SUBJECT is who the fact is ABOUT — often different!
               If Alice says "My brother Tom lives in Paris" → subject=Tom, predicate=lives_in, object=Paris
               Also extract: Alice sibling_of Tom
            2. Extract BOTH stated facts AND implied relationships
            3. Use "Me" for the user's own facts. Use real names, never pronouns.
            4. Keep objects short (names, places, things — not full sentences)
            5. Return [] if no extractable facts exist
            
            Example:
            [10:00] Bob: I just got a new job at Google!
            [10:02] Me: Nice! My sister Jane is allergic to peanuts.
            [10:03] Bob: My mom Linda moved to Chicago last month.
            
            Output:
            [{"subject":"Bob","subject_type":"PERSON","predicate":"works_at","object":"Google","object_type":"ORGANIZATION","confidence":0.9},{"subject":"Jane","subject_type":"PERSON","predicate":"is_allergic_to","object":"peanuts","object_type":"FOOD","confidence":0.9},{"subject":"Me","subject_type":"PERSON","predicate":"sibling_of","object":"Jane","object_type":"PERSON","confidence":0.9},{"subject":"Linda","subject_type":"PERSON","predicate":"moved_to","object":"Chicago","object_type":"PLACE","confidence":0.9},{"subject":"Bob","subject_type":"PERSON","predicate":"parent_of","object":"Linda","object_type":"PERSON","confidence":0.8}]
            
            Predicates (pick closest match):
            owns, lives_in, works_at, works_as, likes, dislikes, is_allergic_to, has_pet, drives, visited,
            birthday_is, member_of, graduated_from, studies_at, favorite_food, hobby_is, married_to,
            engaged_to, dating, sibling_of, parent_of, child_of, friend_of, neighbor_of, plays, watches,
            diagnosed_with, born_in, moved_to, traveled_to, bought, sold, wants, plans_to, nickname_is,
            age_is, prefers, attended, proposed_to, coaches, teaches, manages, retired_from, volunteers_at
            
            Types: PERSON, PLACE, ORGANIZATION, OBJECT, EVENT, FOOD, VEHICLE, PET, MEDICAL, DATE
            
            Extract facts from this conversation (compact JSON array, no markdown):
            %s
            JSON:""";

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

    private EntityResolutionService resolutionService;

    @Autowired
    public void setResolutionService(EntityResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    private final Map<UUID, Boolean> cancelledJobs = new ConcurrentHashMap<>();

    @Value("${smsarchive.ai.kg.model:phi4-mini}")
    private String modelName;

    @Value("${smsarchive.ai.kg.window-char-budget:3000}")
    private int windowCharBudget;

    @Value("${smsarchive.ai.kg.max-message-chars:3000}")
    private int maxMessageChars;

    @Value("${smsarchive.ai.kg.min-body-length:20}")
    private int minBodyLength;

    @Value("${smsarchive.ai.kg.auto-extract:true}")
    private boolean autoExtract;

    @Value("${smsarchive.ai.gpu-cooldown.enabled:false}")
    private boolean cooldownEnabled;

    @Value("${smsarchive.ai.gpu-cooldown.interval:100}")
    private int cooldownInterval;

    @Value("${smsarchive.ai.gpu-cooldown.pause-seconds:60}")
    private int cooldownPauseSeconds;

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
     * Reset orphaned RUNNING/PENDING jobs on startup so they don't block new extractions.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void resetOrphanedJobs() {
        List<KgExtractionJob> orphaned = jobRepository.findByStatusIn(List.of("RUNNING", "PENDING"));
        for (KgExtractionJob job : orphaned) {
            log.warn("Resetting orphaned KG extraction job {} (was {})", job.getId(), job.getStatus());
            job.setStatus("FAILED");
            job.setErrorMessage("Reset on startup — job was orphaned by a restart");
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
        }
        if (!orphaned.isEmpty()) {
            log.info("Reset {} orphaned KG extraction job(s)", orphaned.size());
        }
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

            // Group messages by conversation with body lengths for dynamic windowing
            LinkedHashMap<Long, List<long[]>> byConversation = new LinkedHashMap<>();
            for (Object[] row : rows) {
                Long msgId = ((Number) row[0]).longValue();
                Long convId = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                int bodyLen = ((Number) row[2]).intValue();
                byConversation.computeIfAbsent(convId, k -> new ArrayList<>())
                        .add(new long[]{msgId, bodyLen});
            }

            int windowCount = 0;
            for (var entry : byConversation.entrySet()) {
                List<long[]> msgData = entry.getValue();

                // Build dynamic windows based on character budget
                List<List<Long>> windows = buildDynamicWindows(msgData);

                for (List<Long> windowIds : windows) {
                    if (Boolean.TRUE.equals(cancelledJobs.get(jobId))) {
                        job.setStatus("CANCELLED");
                        jobRepository.save(job);
                        log.info("Extraction job {} cancelled", jobId);
                        return;
                    }

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

                    windowCount++;
                    if (cooldownEnabled && windowCount % cooldownInterval == 0) {
                        log.info("GPU cooldown: pausing {}s after {} windows ({} messages processed)",
                                cooldownPauseSeconds, windowCount, job.getProcessed());
                        try { Thread.sleep(cooldownPauseSeconds * 1000L); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());
            log.info("Extraction job {} completed: {} triples, {} entities from {} messages",
                    jobId, job.getTriplesFound(), job.getEntitiesFound(), job.getProcessed());

            // Run entity resolution (auto-merge duplicates, link contacts)
            if (resolutionService != null) {
                try {
                    resolutionService.runResolution(user);
                } catch (Exception e) {
                    log.warn("Post-extraction entity resolution failed: {}", e.getMessage());
                }
            }

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

        // Build context header (who is this conversation with?)
        String contextHeader = buildContextHeader(messages);

        // Call the LLM with retry (up to 3 attempts with backoff)
        String prompt = String.format(EXTRACTION_PROMPT_TEMPLATE, contextHeader, conversationText);
        String llmOutput = callLlmWithRetry(prompt);
        List<ExtractedFact> facts = parseFacts(llmOutput);

        if (facts.isEmpty() && llmOutput != null && !llmOutput.isBlank()) {
            log.info("KG window: LLM returned non-empty output but parsed 0 facts. Output preview: {}",
                    llmOutput.substring(0, Math.min(200, llmOutput.length())));
        } else if (!facts.isEmpty()) {
            log.debug("KG window: parsed {} facts from {} messages", facts.size(), messageIds.size());
        }

        // Persist in a transaction
        int[] counts = transactionTemplate.execute(status -> {
            int triples = 0;
            int newEntities = 0;

            for (ExtractedFact fact : facts) {
                try {
                    if (!isValidFact(fact)) {
                        log.debug("Skipping invalid fact structure: [{} {} {}]",
                                fact.subject, fact.predicate, fact.object);
                        continue;
                    }
                    if (!isValidEntity(fact.subject) ||
                            (fact.object != null && !fact.object.isBlank() && !isValidEntity(fact.object))) {
                        log.debug("Skipping fact with invalid entity name: [{} {} {}]",
                                fact.subject, fact.predicate, fact.object);
                        continue;
                    }
                    // Drop facts that fall back to "related_to" — too noisy
                    String predicate = normalizePredicate(fact.predicate);
                    if ("related_to".equals(predicate)) {
                        log.debug("Skipping non-canonical predicate '{}': [{} {} {}]",
                                fact.predicate, fact.subject, fact.predicate, fact.object);
                        continue;
                    }
                    int created = persistFact(fact, user, messageIds);
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

    /**
     * Build windows of message IDs based on character budget rather than fixed count.
     * Each window will contain as many full messages as fit within windowCharBudget.
     * A single large message gets its own window (truncated to maxMessageChars).
     */
    private List<List<Long>> buildDynamicWindows(List<long[]> msgData) {
        List<List<Long>> windows = new ArrayList<>();
        List<Long> currentWindow = new ArrayList<>();
        int currentChars = 0;

        for (long[] data : msgData) {
            long msgId = data[0];
            int bodyLen = (int) Math.min(data[1], maxMessageChars);

            if (!currentWindow.isEmpty() && currentChars + bodyLen > windowCharBudget) {
                windows.add(currentWindow);
                currentWindow = new ArrayList<>();
                currentChars = 0;
            }

            currentWindow.add(msgId);
            currentChars += bodyLen;
        }

        if (!currentWindow.isEmpty()) {
            windows.add(currentWindow);
        }

        return windows;
    }

    // ---- LLM call with retry ----

    private static final int LLM_TIMEOUT_SECONDS = 90;

    private String callLlmWithRetry(String prompt) {
        Exception lastException = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Future<String> future = CompletableFuture.supplyAsync(() -> {
                    ChatResponse response = chatModel.call(
                            new Prompt(prompt, OllamaOptions.builder()
                                    .model(modelName)
                                    .temperature(0.3)
                                    .numCtx(8192)
                                    .build()));
                    return response.getResult().getOutput().getText();
                });
                return future.get(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                lastException = e;
                log.warn("LLM call attempt {} timed out after {}s — skipping window",
                        attempt + 1, LLM_TIMEOUT_SECONDS);
            } catch (ExecutionException e) {
                lastException = e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
                log.warn("LLM call attempt {} failed: {}", attempt + 1, lastException.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during LLM call", e);
            }
            long delay = 2000L * (1 << attempt); // 2s, 4s, 8s
            try { Thread.sleep(delay); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during retry", ie);
            }
        }
        throw new RuntimeException("LLM call failed after 3 attempts", lastException);
    }

    // ---- Prompt building ----

    private String buildContextHeader(List<Message> messages) {
        // Identify the conversation partner(s) from already-loaded sender contacts
        Set<String> contacts = new LinkedHashSet<>();
        for (Message m : messages) {
            if (m.getDirection() == MessageDirection.INBOUND) {
                if (m.getSenderContact() != null && m.getSenderContact().getName() != null) {
                    contacts.add(m.getSenderContact().getName());
                }
            }
        }
        StringBuilder sb = new StringBuilder("Context: This is a text message conversation between Me");
        if (!contacts.isEmpty()) {
            sb.append(" and ").append(String.join(", ", contacts));
        }
        sb.append(".");
        return sb.toString();
    }

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
              .append(truncate(m.getBody(), maxMessageChars)).append("\n");
        }
        return sb.toString();
    }

    // ---- LLM output parsing ----

    private List<ExtractedFact> parseFacts(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) return List.of();

        String json = extractJsonArray(llmOutput);
        if (json == null || json.equals("[]")) return List.of();

        // Normalize whitespace (phi4-mini sometimes pretty-prints)
        json = json.replaceAll("\\s*\n\\s*", " ").replaceAll("\\s+", " ");

        // Fix unquoted JSON keys: {subject: "Bob"} → {"subject": "Bob"}
        json = json.replaceAll("(?<=\\{|,)\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*:", " \"$1\":");

        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.info("Failed to parse LLM output as JSON: {} — raw: {}", e.getMessage(),
                    llmOutput.substring(0, Math.min(500, llmOutput.length())));
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

    // ---- Validation ----

    private boolean isValidEntity(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        if (trimmed.length() < 2) return false;
        if (REJECTED_ENTITY_NAMES.contains(trimmed.toLowerCase())) return false;
        // Reject if it's all punctuation or digits
        if (trimmed.matches("^[\\p{Punct}\\d\\s]+$")) return false;
        // Reject sentence-length values — real entities/values are short
        if (trimmed.length() > 80) return false;
        return true;
    }

    private boolean isValidFact(ExtractedFact fact) {
        if (fact.subject == null || fact.subject.isBlank()) return false;
        if (fact.predicate == null || fact.predicate.isBlank()) return false;
        // Object must exist and not be a sentence
        String obj = fact.object;
        if (obj == null || obj.isBlank()) return false;
        if (obj.trim().length() > 80) return false;
        // Reject if the object contains sentence-like patterns (multiple spaces + verb-like words)
        if (obj.split("\\s+").length > 8) return false;
        return true;
    }

    // ---- Entity and triple persistence ----

    private static final float CONFIDENCE_BOOST_PER_SOURCE = 0.1f;

    /**
     * Persist a single extracted fact with dedup and confidence boosting.
     * If the same triple already exists, boost confidence and link new source messages.
     * Returns the number of NEW entities created (0, 1, or 2).
     */
    private int persistFact(ExtractedFact fact, User user, List<Long> sourceMessageIds) {
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

        // Create or update triple (dedup by subject+predicate+object)
        String predicate = normalizePredicate(fact.predicate);
        float baseConfidence = Math.max(0.1f, Math.min(1.0f,
                fact.confidence > 0 ? fact.confidence : 0.5f));

        // Check if this exact fact already exists
        Optional<KgTriple> existing = tripleRepository.findByUserAndSubjectAndPredicateAndObjectOrValue(
                user, subject, predicate, objectEntity, objectValue);

        KgTriple triple;
        if (existing.isPresent()) {
            // Boost confidence on existing triple
            triple = existing.get();
            float boosted = Math.min(1.0f, triple.getConfidence() + CONFIDENCE_BOOST_PER_SOURCE);
            triple.setConfidence(boosted);
            tripleRepository.save(triple);
        } else {
            // Create new triple
            triple = new KgTriple();
            triple.setUser(user);
            triple.setSubject(subject);
            triple.setPredicate(predicate);
            triple.setObject(objectEntity);
            triple.setObjectValue(objectValue);
            triple.setConfidence(baseConfidence);
            triple.setIsVerified(false);
            triple.setIsNegated(false);
            // Link to first message in window as primary source
            if (!sourceMessageIds.isEmpty()) {
                Message sourceRef = new Message();
                sourceRef.setId(sourceMessageIds.get(0));
                triple.setSourceMessage(sourceRef);
            }
            tripleRepository.save(triple);
        }

        // Link all source messages to this triple
        for (Long msgId : sourceMessageIds) {
            jdbcTemplate.update(
                    "INSERT INTO kg_triple_sources (triple_id, message_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    triple.getId(), msgId);
        }

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
        String normalized = predicate.trim().toLowerCase().replaceAll("\\s+", "_");
        if (CANONICAL_PREDICATES.contains(normalized)) return normalized;
        // Try common variations
        String stripped = normalized.replaceAll("^(has_|is_|was_|did_)", "");
        if (CANONICAL_PREDICATES.contains(stripped)) return stripped;
        return "related_to";
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
