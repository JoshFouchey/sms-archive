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
import java.util.regex.Pattern;

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
            "manages", "retired_from", "serves_in", "volunteers_at",
            "quit", "divorced", "dropped_out");

    // Map phi4-mini's creative predicates to canonical ones
    private static final Map<String, String> PREDICATE_ALIASES = Map.ofEntries(
            // Variations phi4-mini produces
            Map.entry("has", "has_pet"),          // context-dependent, but usually pets
            Map.entry("allergic_to", "is_allergic_to"),
            Map.entry("diagnosed", "diagnosed_with"),
            Map.entry("was_diagnosed_with", "diagnosed_with"),
            Map.entry("favorite_show", "watches"),
            Map.entry("favorite_movie", "watches"),
            Map.entry("favorite_band", "likes"),
            Map.entry("favorite_restaurant", "likes"),
            Map.entry("wants_to_visit", "wants"),
            Map.entry("plans_to_visit", "plans_to"),
            Map.entry("planning_trip", "plans_to"),
            Map.entry("stayed_with", "visited"),
            Map.entry("still_loves", "likes"),
            Map.entry("loves", "likes"),
            Map.entry("enjoys", "likes"),
            Map.entry("hates", "dislikes"),
            Map.entry("gifted", "owns"),
            Map.entry("gift_to", "owns"),
            Map.entry("received", "owns"),
            Map.entry("works_for", "works_at"),
            Map.entry("worked_at", "works_at"),
            Map.entry("employed_at", "works_at"),
            Map.entry("employed_by", "works_at"),
            Map.entry("moved_from", "born_in"),
            Map.entry("living_in", "lives_in"),
            Map.entry("resides_in", "lives_in"),
            Map.entry("sister_of", "sibling_of"),
            Map.entry("brother_of", "sibling_of"),
            Map.entry("mother_of", "parent_of"),
            Map.entry("father_of", "parent_of"),
            Map.entry("son_of", "child_of"),
            Map.entry("daughter_of", "child_of"),
            Map.entry("pet_name", "has_pet"),
            Map.entry("got", "owns"),
            Map.entry("purchased", "bought"),
            Map.entry("studying", "studies_at"),
            Map.entry("attending", "studies_at"),
            Map.entry("coaches_at", "coaches"),
            Map.entry("teaches_at", "teaches"),
            Map.entry("teach_at", "teaches"),
            // Base-form verbs phi4-mini uses (missing third-person -s)
            Map.entry("live_in", "lives_in"),
            Map.entry("work_at", "works_at"),
            Map.entry("work_as", "works_as"),
            Map.entry("study_at", "studies_at"),
            Map.entry("drive", "drives"),
            Map.entry("own", "owns"),
            Map.entry("play", "plays"),
            Map.entry("watch", "watches"),
            Map.entry("anniversary_with", "married_to"),
            Map.entry("going_with", "friend_of"),
            Map.entry("going_for", "plans_to"),
            Map.entry("traveled_with", "traveled_to"),
            Map.entry("celebrated_anniversary", "married_to"),
            Map.entry("took_up", "hobby_is"),
            Map.entry("picked_up", "hobby_is"),
            Map.entry("started", "hobby_is"),
            Map.entry("take_medication_for", "takes_medication"),
            Map.entry("takes_medication_for", "takes_medication"),
            Map.entry("on_medication_for", "takes_medication"),
            Map.entry("has_allergy_to", "is_allergic_to"),
            Map.entry("gave_gift_to", "bought"),
            Map.entry("gave", "bought"),
            Map.entry("planning", "plans_to"),
            Map.entry("has_sister", "sibling_of"),
            Map.entry("has_brother", "sibling_of")
    );

    // Singular predicates: a person can only have ONE value at a time.
    // If a new fact contradicts an existing one, it's a real conflict.
    // All other predicates are "plural" — multiple values accumulate (owns, likes, visited, etc.)
    private static final Set<String> SINGULAR_PREDICATES = Set.of(
            "lives_in", "works_at", "works_as", "married_to", "engaged_to",
            "dating", "birthday_is", "age_is", "born_in", "email_is",
            "phone_number_is", "nickname_is", "studies_at");

    // Negation map: when a "trigger" predicate is extracted, it supersedes matching facts
    // with the "target" predicates for the SAME subject+object.
    // Example: "Tom sold Mustang" → supersedes "Tom owns Mustang" AND "Tom bought Mustang"
    private static final Map<String, List<String>> NEGATION_MAP = Map.ofEntries(
            Map.entry("sold", List.of("owns", "bought")),
            Map.entry("lost", List.of("owns")),
            Map.entry("broke", List.of("owns")),
            Map.entry("moved_to", List.of("lives_in")),
            Map.entry("divorced", List.of("married_to", "engaged_to")),
            Map.entry("quit", List.of("works_at")),
            Map.entry("retired_from", List.of("works_at")),
            Map.entry("dropped_out", List.of("studies_at")),
            Map.entry("graduated_from", List.of("studies_at")),
            Map.entry("dislikes", List.of("likes", "favorite_food"))
    );

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
            age_is, prefers, attended, proposed_to, coaches, teaches, manages, retired_from, volunteers_at,
            quit, divorced, dropped_out, lost, broke
            
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
                        log.error("Extraction window failed (conv {}, {} msgs): {}",
                                entry.getKey(), windowIds.size(), e.getMessage(), e);
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
        log.debug("KG prompt ({} chars): {}", prompt.length(), prompt.substring(0, Math.min(500, prompt.length())));
        String llmOutput = callLlmWithRetry(prompt);
        List<ExtractedFact> facts = parseFacts(llmOutput);

        log.info("KG window: LLM returned {} chars, parsed {} facts from {} messages (prompt {} chars)",
                llmOutput != null ? llmOutput.length() : 0, facts.size(), messageIds.size(), prompt.length());
        if (facts.isEmpty() && llmOutput != null && !llmOutput.isBlank() && llmOutput.length() > 2) {
            log.warn("KG window: 0 facts from non-trivial LLM response. Raw output:\n{}", llmOutput);
        }

        // Persist in a transaction
        int[] counts = transactionTemplate.execute(status -> {
            int triples = 0;
            int newEntities = 0;
            int skippedInvalid = 0;
            int skippedEntity = 0;
            int skippedPredicate = 0;
            int skippedError = 0;

            // Use earliest message timestamp as fact_date for temporal tracking
            Instant factDate = messages.stream()
                    .map(Message::getTimestamp)
                    .filter(Objects::nonNull)
                    .min(Instant::compareTo)
                    .orElse(Instant.now());

            for (ExtractedFact fact : facts) {
                try {
                    if (!isValidFact(fact)) {
                        log.debug("Skipping invalid fact structure: [{} {} {}]",
                                fact.subject, fact.predicate, fact.object);
                        skippedInvalid++;
                        continue;
                    }
                    if (!isValidEntity(fact.subject) ||
                            (fact.object != null && !fact.object.isBlank() && !isValidEntity(fact.object))) {
                        log.debug("Skipping fact with invalid entity name: [{} {} {}]",
                                fact.subject, fact.predicate, fact.object);
                        skippedEntity++;
                        continue;
                    }
                    // Drop facts that fall back to "related_to" — too noisy
                    String predicate = normalizePredicate(fact.predicate);
                    if ("related_to".equals(predicate)) {
                        log.debug("Skipping non-canonical predicate '{}': [{} {} {}]",
                                fact.predicate, fact.subject, fact.predicate, fact.object);
                        skippedPredicate++;
                        continue;
                    }
                    int created = persistFact(fact, user, messageIds, factDate);
                    triples++;
                    newEntities += created;
                } catch (Exception e) {
                    log.debug("Skipping fact [{} {} {}]: {}",
                            fact.subject, fact.predicate, fact.object, e.getMessage());
                    skippedError++;
                }
            }

            if (skippedInvalid + skippedEntity + skippedPredicate + skippedError > 0) {
                log.info("KG window: {} persisted, {} skipped (invalid={}, entity={}, predicate={}, error={})",
                        triples, skippedInvalid + skippedEntity + skippedPredicate + skippedError,
                        skippedInvalid, skippedEntity, skippedPredicate, skippedError);
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

    List<ExtractedFact> parseFacts(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) return List.of();

        String json = extractJsonArray(llmOutput);
        if (json == null || json.equals("[]")) return List.of();

        json = sanitizeLlmJson(json);

        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            // First parse failed — try extracting individual objects
            log.info("JSON parse failed ({}), attempting object-by-object recovery", e.getMessage());
            List<ExtractedFact> recovered = recoverFactsFromMalformedJson(json);
            if (!recovered.isEmpty()) {
                log.info("Recovered {} facts from malformed JSON", recovered.size());
                return recovered;
            }
            log.warn("Failed to parse LLM output as JSON: {} — raw (first 800 chars): {}", e.getMessage(),
                    llmOutput.substring(0, Math.min(800, llmOutput.length())));
            return List.of();
        }
    }

    /**
     * Sanitize common LLM JSON quirks: unquoted keys, single quotes,
     * trailing commas, newlines inside strings, etc.
     */
    private String sanitizeLlmJson(String json) {
        // Strip markdown code fences if present
        json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");

        // Normalize whitespace (phi4-mini sometimes pretty-prints)
        json = json.replaceAll("\\s*\n\\s*", " ").replaceAll("\\s+", " ");

        // Fix = used as key-value separator without closing quote on key:
        // phi4-mini writes "object="value" instead of "object":"value"
        json = json.replaceAll("\"([a-zA-Z_][a-zA-Z0-9_]*)=", "\"$1\":");

        // Fix single quotes → double quotes
        json = json.replace("'", "\"");

        // Fix unquoted JSON keys: {subject: "Bob"} → {"subject": "Bob"}
        json = json.replaceAll("(?<=\\{|,)\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*:", " \"$1\":");

        // Fix trailing commas: [{"a":"b"},] → [{"a":"b"}]
        json = json.replaceAll(",\\s*]", "]").replaceAll(",\\s*}", "}");

        // Fix duplicate keys (phi4-mini sometimes emits "object_type" twice) — keep first
        // This is a best-effort fix; Jackson will just use the last value
        return json;
    }

    /**
     * Try to recover individual fact objects from malformed JSON array.
     * Splits on },{ boundaries and parses each object individually.
     */
    private List<ExtractedFact> recoverFactsFromMalformedJson(String json) {
        List<ExtractedFact> recovered = new ArrayList<>();

        // Find individual JSON objects within the array
        Pattern objPattern = Pattern.compile("\\{[^{}]+\\}");
        var matcher = objPattern.matcher(json);

        while (matcher.find()) {
            String objStr = sanitizeLlmJson(matcher.group());
            try {
                ExtractedFact fact = objectMapper.readValue(objStr, ExtractedFact.class);
                if (fact.subject != null && fact.predicate != null) {
                    recovered.add(fact);
                }
            } catch (Exception e) {
                log.debug("Could not recover individual fact object: {}", objStr);
            }
        }

        return recovered;
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
    private static final float ENTITY_SIMILARITY_THRESHOLD = 0.75f;

    /**
     * Persist a single extracted fact with smart dedup, conflict detection, and temporal tracking.
     *
     * Logic:
     * 1. Hash check — if exact same fact exists, update last_seen_at + bump confidence
     * 2. Soft conflict — same subject+predicate but different object → flag as conflict cluster
     * 3. New fact — create with fact_date, status=ACTIVE, confidence weighted by source length
     *
     * Returns the number of NEW entities created (0, 1, or 2).
     */
    int persistFact(ExtractedFact fact, User user, List<Long> sourceMessageIds,
                            Instant factDate) {
        if (fact.subject == null || fact.predicate == null) {
            throw new IllegalArgumentException("Subject and predicate are required");
        }

        String subjectType = normalizeEntityType(fact.subjectType);
        int newEntities = 0;

        // Find or create subject (with fuzzy disambiguation)
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

        String predicate = normalizePredicate(fact.predicate);
        float baseConfidence = Math.max(0.1f, Math.min(1.0f,
                fact.confidence > 0 ? fact.confidence : 0.5f));

        // Generate fact hash for exact dedup: normalized(subject_name + predicate + object_name/value)
        final KgEntity finalObjectEntity = objectEntity;
        final String finalObjectValue = objectValue;
        String objectKey = finalObjectEntity != null ? finalObjectEntity.getCanonicalName() : (finalObjectValue != null ? finalObjectValue : "");
        String factHash = generateFactHash(subject.getCanonicalName(), predicate, objectKey);

        // --- Step 1: Hash check — exact same fact already exists? ---
        Optional<KgTriple> byHash = tripleRepository.findByUserAndFactHash(user, factHash);
        if (byHash.isPresent()) {
            KgTriple existing = byHash.get();
            // Same fact seen again — update last_seen_at and bump confidence
            float boosted = Math.min(1.0f, existing.getConfidence() + CONFIDENCE_BOOST_PER_SOURCE);
            existing.setConfidence(boosted);
            existing.setLastSeenAt(Instant.now());
            tripleRepository.save(existing);
            linkSourceMessages(existing.getId(), sourceMessageIds);
            return newEntities;
        }

        // --- Step 2: Negation map — "sold Mustang" supersedes "owns Mustang" ---
        List<String> negatedPredicates = NEGATION_MAP.get(predicate);
        if (negatedPredicates != null && finalObjectEntity != null) {
            for (String targetPredicate : negatedPredicates) {
                // For singular targets (lives_in, works_at), supersede ALL values
                // For plural targets (owns, bought), supersede only the matching object
                List<KgTriple> toSupersede;
                if (SINGULAR_PREDICATES.contains(targetPredicate)) {
                    toSupersede = tripleRepository.findActiveBySubjectAndPredicate(
                            user, subject, targetPredicate);
                } else {
                    toSupersede = tripleRepository.findActiveBySubjectPredicateAndObject(
                            user, subject, targetPredicate, finalObjectEntity);
                }

                for (KgTriple old : toSupersede) {
                    old.setStatus("SUPERSEDED");
                    tripleRepository.save(old);
                    log.info("Negation: [{} {} {}] superseded by [{} {} {}]",
                            old.getSubject().getCanonicalName(), old.getPredicate(),
                            old.getObject() != null ? old.getObject().getCanonicalName() : old.getObjectValue(),
                            subject.getCanonicalName(), predicate, objectKey);
                }
            }
        }

        // --- Step 3: Soft conflict check — only for SINGULAR predicates ---
        // "Tom lives_in NYC" + "Tom lives_in Chicago" → conflict (can only live in one place)
        // "Tom owns Mustang" + "Tom owns Civic" → NOT a conflict (can own multiple cars)
        Long conflictClusterId = null;
        if (SINGULAR_PREDICATES.contains(predicate)) {
            List<KgTriple> activeConflicts = tripleRepository.findActiveBySubjectAndPredicate(
                    user, subject, predicate);

            if (!activeConflicts.isEmpty()) {
                boolean isConflict = activeConflicts.stream().anyMatch(existing -> {
                    if (finalObjectEntity != null && existing.getObject() != null) {
                        return !existing.getObject().getId().equals(finalObjectEntity.getId());
                    }
                    if (finalObjectValue != null && existing.getObjectValue() != null) {
                        return !existing.getObjectValue().equalsIgnoreCase(finalObjectValue);
                    }
                    return false;
                });

                if (isConflict) {
                    conflictClusterId = activeConflicts.stream()
                            .map(KgTriple::getConflictClusterId)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElseGet(() -> tripleRepository.nextConflictClusterId(user.getId()));

                    for (KgTriple existing : activeConflicts) {
                        if (existing.getConflictClusterId() == null) {
                            existing.setConflictClusterId(conflictClusterId);
                            existing.setStatus("FLAGGED");
                            tripleRepository.save(existing);
                        }
                    }

                    log.info("Conflict detected: [{} {} {}] vs existing facts. Cluster ID: {}",
                            subject.getCanonicalName(), predicate, objectKey, conflictClusterId);
                }
            }
        }

        // --- Step 3: Create new triple ---
        KgTriple triple = new KgTriple();
        triple.setUser(user);
        triple.setSubject(subject);
        triple.setPredicate(predicate);
        triple.setObject(finalObjectEntity);
        triple.setObjectValue(finalObjectValue);
        triple.setConfidence(baseConfidence);
        triple.setIsVerified(false);
        triple.setIsNegated(false);
        triple.setFactHash(factHash);
        triple.setFactDate(factDate);
        triple.setStatus(conflictClusterId != null ? "FLAGGED" : "ACTIVE");
        triple.setConflictClusterId(conflictClusterId);
        triple.setLastSeenAt(Instant.now());

        if (!sourceMessageIds.isEmpty()) {
            Message sourceRef = new Message();
            sourceRef.setId(sourceMessageIds.get(0));
            triple.setSourceMessage(sourceRef);
        }
        tripleRepository.save(triple);

        linkSourceMessages(triple.getId(), sourceMessageIds);
        return newEntities;
    }

    private void linkSourceMessages(Long tripleId, List<Long> sourceMessageIds) {
        for (Long msgId : sourceMessageIds) {
            jdbcTemplate.update(
                    "INSERT INTO kg_triple_sources (triple_id, message_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    tripleId, msgId);
        }
    }

    /**
     * Generate a deterministic hash for a fact triple.
     * Normalized: lowercase, trimmed, sorted to avoid order-dependent mismatches.
     */
    String generateFactHash(String subjectName, String predicate, String objectKey) {
        String normalized = (subjectName + "|" + predicate + "|" + objectKey)
                .toLowerCase().trim();
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all JVMs
            throw new RuntimeException(e);
        }
    }

    /**
     * Find or create an entity with fuzzy disambiguation.
     * Before creating a new entity, checks pg_trgm similarity against existing entities
     * and their aliases. If "Tommy" is similar enough to existing "Tom", reuses "Tom"
     * and adds "Tommy" as an alias.
     */
    KgEntity findOrCreateEntity(User user, String name, String entityType) {
        // Exact match first (fast path)
        Optional<KgEntity> exact = entityRepository.findByUserAndCanonicalNameAndEntityType(
                user, name, entityType);
        if (exact.isPresent()) return exact.get();

        // Fuzzy match — check if a similar entity already exists
        if ("PERSON".equals(entityType)) {
            List<KgEntity> similar = entityRepository.findSimilarByNameOrAlias(
                    user.getId(), name, entityType, ENTITY_SIMILARITY_THRESHOLD);

            if (!similar.isEmpty()) {
                KgEntity match = similar.get(0);
                // Add this name as an alias of the existing entity
                jdbcTemplate.update("""
                        INSERT INTO kg_entity_aliases (entity_id, alias, source, confidence, created_at)
                        VALUES (?, ?, 'EXTRACTED', 0.7, now())
                        ON CONFLICT (entity_id, alias) DO NOTHING
                        """, match.getId(), name);
                log.debug("Entity disambiguation: '{}' matched to existing '{}' (type: {})",
                        name, match.getCanonicalName(), entityType);
                return match;
            }
        }

        // No match — create new entity
        KgEntity entity = new KgEntity();
        entity.setUser(user);
        entity.setCanonicalName(name);
        entity.setEntityType(entityType);
        return entityRepository.save(entity);
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

    String normalizePredicate(String predicate) {
        if (predicate == null) return "related_to";
        String normalized = predicate.trim().toLowerCase().replaceAll("\\s+", "_");
        if (CANONICAL_PREDICATES.contains(normalized)) return normalized;
        // Check alias map
        String aliased = PREDICATE_ALIASES.get(normalized);
        if (aliased != null) return aliased;
        // Try stripping common prefixes: is_allergic_to → allergic_to, still_plays → plays
        String stripped = normalized.replaceAll("^(has_|is_|was_|did_|have_|still_)", "");
        if (CANONICAL_PREDICATES.contains(stripped)) return stripped;
        aliased = PREDICATE_ALIASES.get(stripped);
        if (aliased != null) return aliased;
        // Try adding 's' for base forms: live_in → lives_in, own → owns, teach → teaches
        String withS = normalized.endsWith("_") ? normalized + "s" : normalized + "s";
        if (CANONICAL_PREDICATES.contains(withS)) return withS;
        // Try "Xes" for verbs ending in consonant: teach → teaches
        String withEs = normalized + "es";
        if (CANONICAL_PREDICATES.contains(withEs)) return withEs;
        // Try removing suffixes: -ed, -ing (handle double consonant: planning → plan)
        String stemmed = normalized.replaceAll("(ed|ing)$", "");
        // Double consonant collapse: plann → plan, runn → run
        stemmed = stemmed.replaceAll("(.)\\1$", "$1");
        if (CANONICAL_PREDICATES.contains(stemmed)) return stemmed;
        aliased = PREDICATE_ALIASES.get(stemmed);
        if (aliased != null) return aliased;
        // Try stem + s: play(ing) → play → plays
        if (CANONICAL_PREDICATES.contains(stemmed + "s")) return stemmed + "s";
        if (CANONICAL_PREDICATES.contains(stemmed + "es")) return stemmed + "es";
        // Compound predicates: "plans_to_grab_dinner" → check if starts with canonical
        for (String canonical : CANONICAL_PREDICATES) {
            if (normalized.startsWith(canonical + "_")) return canonical;
        }
        // Also try compound after stripping prefix
        for (String canonical : CANONICAL_PREDICATES) {
            if (stripped.startsWith(canonical + "_")) return canonical;
        }
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
