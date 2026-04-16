package com.joshfouchey.smsarchive.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.joshfouchey.smsarchive.event.ImportCompletedEvent;
import com.joshfouchey.smsarchive.model.*;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import com.joshfouchey.smsarchive.service.importpipeline.ContactResolver;
import com.joshfouchey.smsarchive.service.importpipeline.ConversationAssigner;
import com.joshfouchey.smsarchive.service.importpipeline.DuplicateDetector;
import com.joshfouchey.smsarchive.service.importpipeline.MediaHandler;
import com.joshfouchey.smsarchive.service.importpipeline.XmlMessageParser;
import com.joshfouchey.smsarchive.service.importpipeline.XmlMessageParser.ElementContext;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import org.apache.commons.io.input.CountingInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class ImportService {

    private final MessageRepository messageRepo;
    private final ContactRepository contactRepo;
    private final ThumbnailService thumbnailService;
    private final ConversationService conversationService;
    private final UserRepository userRepository;
    private TaskExecutor importTaskExecutor;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final DuplicateDetector duplicateDetector;
    private final ContactResolver contactResolver;
    private final ConversationAssigner conversationAssigner;
    private MediaHandler mediaHandler;
    private XmlMessageParser xmlParser;
    private final ThreadLocal<User> threadLocalImportUser = new ThreadLocal<>();

    private final Cache<UUID, ImportProgress> progressMap = Caffeine.newBuilder()
            .maximumSize(50)
            .expireAfterWrite(java.time.Duration.ofHours(1))
            .build();

    @Value("${smsarchive.import.inline:false}")
    private boolean importInline;

    @Value("${smsarchive.import.batchSize:500}")
    private int streamBatchSize;

    @Value("${smsarchive.media.root:./media/messages}")
    private String mediaRoot;

    Path getMediaRoot() {
        return Paths.get(mediaRoot);
    }

    @PostConstruct
    private void logMediaRootAtStartup() {
        log.info("Resolved media root: {}", Paths.get(mediaRoot).toAbsolutePath());
        mediaRelocationHelper = new MediaRelocationHelper(thumbnailService, getMediaRoot());
        mediaHandler = new MediaHandler(thumbnailService, getMediaRoot());
        xmlParser = new XmlMessageParser(contactResolver, mediaHandler, mediaRelocationHelper, this::resolveImportUser);
    }

    public ImportService(MessageRepository messageRepo, ContactRepository contactRepo,
                         CurrentUserProvider currentUserProvider, ThumbnailService thumbnailService,
                         ConversationService conversationService, UserRepository userRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.messageRepo = messageRepo;
        this.contactRepo = contactRepo;
        this.currentUserProvider = currentUserProvider;
        this.thumbnailService = thumbnailService;
        this.conversationService = conversationService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.duplicateDetector = new DuplicateDetector(messageRepo);
        this.contactResolver = new ContactResolver(contactRepo);
        this.conversationAssigner = new ConversationAssigner(conversationService);
    }

    @Autowired(required = false)
    public void setImportTaskExecutor(@Qualifier("importTaskExecutor") TaskExecutor executor) {
        this.importTaskExecutor = executor;
    }

    // visible for testing
    Instant parseInstant(String millisStr) {
        ensureXmlParser();
        return xmlParser.parseInstant(millisStr);
    }

    private void ensureXmlParser() {
        if (xmlParser == null) {
            ensureMediaHelper();
            xmlParser = new XmlMessageParser(contactResolver, mediaHandler, mediaRelocationHelper, this::resolveImportUser);
        }
    }

    // ===== Streaming Import (Large XML) =====
    @CacheEvict(value = {"analyticsDashboard", "contactSummaries", "conversationList", "distinctContacts", "conversationTimeline"}, allEntries = true)
    public UUID startImportAsync(Path xmlPath) throws Exception {
        ensureMediaHelper();
        UUID jobId = UUID.randomUUID();
        long size = Files.size(xmlPath);
        ImportProgress progress = new ImportProgress(jobId, size);
        progressMap.put(jobId, progress);
        // Capture authenticated user now
        User importUser = currentUserProvider.getCurrentUser();
        Runnable task = () -> {
            threadLocalImportUser.set(importUser);
            try { runStreamingImportAsync(jobId, xmlPath); }
            finally { threadLocalImportUser.remove(); }
        };
        if (importInline) {
            task.run();
            return jobId;
        }
        if (importTaskExecutor != null) {
            importTaskExecutor.execute(task);
        } else {
            // Fallback: create a dedicated thread
            Thread t = new Thread(task, "import-worker-fallback-" + jobId);
            t.setDaemon(true);
            t.start();
        }
        return jobId;
    }

    /**
     * Start import for a specific user by username (used by ImportDirectoryWatcher).
     * This method does not require an authenticated security context.
     */
    @CacheEvict(value = {"analyticsDashboard", "contactSummaries", "conversationList", "distinctContacts", "conversationTimeline"}, allEntries = true)
    public UUID startImportAsyncForUser(Path xmlPath, String username) throws Exception {
        ensureMediaHelper();

        // Look up user by username
        User importUser = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        UUID jobId = UUID.randomUUID();
        long size = Files.size(xmlPath);
        ImportProgress progress = new ImportProgress(jobId, size);
        progressMap.put(jobId, progress);

        Runnable task = () -> {
            threadLocalImportUser.set(importUser);
            try {
                runStreamingImportAsync(jobId, xmlPath);
            } finally {
                threadLocalImportUser.remove();
            }
        };

        if (importInline) {
            task.run();
            return jobId;
        }

        if (importTaskExecutor != null) {
            importTaskExecutor.execute(task);
        } else {
            // Fallback: create a dedicated thread
            Thread t = new Thread(task, "import-worker-" + username + "-" + jobId);
            t.setDaemon(true);
            t.start();
        }

        return jobId;
    }

    public ImportProgress getProgress(UUID id) { return progressMap.getIfPresent(id); }

    private XMLStreamReader createSecureXmlStreamReader(CountingInputStream cis) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        try {
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
        } catch (IllegalArgumentException _) { }
        try {
            factory.setProperty("javax.xml.stream.supportDTD", Boolean.FALSE);
        } catch (IllegalArgumentException _) { }
        try {
            factory.setProperty("javax.xml.stream.isReplacingEntityReferences", Boolean.TRUE);
        } catch (IllegalArgumentException _) { }
        return factory.createXMLStreamReader(cis);
    }

    private void handleSmsReady(Message msg, String suggestedName,
                                ImportProgress progress, Set<String> seenKeys, List<Message> batch) {
        conversationAssigner.assignConversationForSms(msg, suggestedName, resolveImportUser());
        boolean dup = duplicateDetector.isDuplicateInRunOrDb(msg, seenKeys, batch);
        if (dup) {
            progress.incDuplicateMessages();
        } else {
            batch.add(msg);
            progress.incImportedMessages();
        }
        progress.incProcessedMessages();
        flushStreamingIfNeeded(batch, progress);
    }

    private void handleMultipartReady(Message msg, String threadKey, Set<String> participantNumbers,
                                      String suggestedName, ImportProgress progress,
                                      Set<String> seenKeys, List<Message> batch) {
        conversationAssigner.assignConversationForMultipart(msg, threadKey, participantNumbers, suggestedName, resolveImportUser());
        boolean dup = duplicateDetector.isDuplicateInRunOrDb(msg, seenKeys, batch);
        if (dup) { progress.incDuplicateMessages(); }
        else { batch.add(msg); progress.incImportedMessages(); }
        progress.incProcessedMessages();
        flushStreamingIfNeeded(batch, progress);
    }

    protected void runStreamingImportAsync(UUID jobId, Path xmlPath) {
        ImportProgress progress = progressMap.getIfPresent(jobId);
        if (progress == null) { log.warn("No progress entry for job {}", jobId); return; }
        progress.setStatus("RUNNING");
        progress.setStartedAt(Instant.now());
        List<Message> batch = new ArrayList<>(streamBatchSize);
        Set<String> seenKeys = new HashSet<>();
        ensureXmlParser();
        try (CountingInputStream cis = new CountingInputStream(Files.newInputStream(xmlPath))) {
            XMLStreamReader r = createSecureXmlStreamReader(cis);
            ElementContext ctx = new ElementContext();
            XmlMessageParser.SmsReadyHandler onSms = (msg, name) ->
                    handleSmsReady(msg, name, progress, seenKeys, batch);
            XmlMessageParser.MultipartReadyHandler onMultipart = (msg, threadKey, participants, name) ->
                    handleMultipartReady(msg, threadKey, participants, name, progress, seenKeys, batch);
            while (r.hasNext()) {
                int evt = r.next();
                if (evt == XMLStreamConstants.START_ELEMENT) {
                    xmlParser.handleStartElement(r, ctx, onSms);
                } else if (evt == XMLStreamConstants.END_ELEMENT) {
                    xmlParser.handleEndElement(r, ctx, onMultipart);
                }
                progress.setBytesRead(cis.getByteCount());
            }
            flushStreamingBatch(batch, progress);
            r.close();
            progress.setDuplicateMessages((int) progress.getDuplicateMessages());
            progress.setStatus("COMPLETED");
            progress.setFinishedAt(Instant.now());
            log.info("Streaming import {} completed: imported={}, duplicates={}", jobId, progress.getImportedMessages(), progress.getDuplicateMessages());

            User importUser = threadLocalImportUser.get();
            if (importUser != null && progress.getImportedMessages() > 0) {
                try {
                    eventPublisher.publishEvent(
                            new ImportCompletedEvent(this, importUser, progress.getImportedMessages()));
                } catch (Exception e) {
                    log.warn("Failed to publish import completed event: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Streaming import {} failed", jobId, e);
            progress.setStatus("FAILED");
            progress.setError(e.getMessage());
            progress.setFinishedAt(Instant.now());
        }
    }

    private User resolveImportUser() {
        User user = threadLocalImportUser.get();
        return (user != null) ? user : currentUserProvider.getCurrentUser();
    }

    private MediaRelocationHelper mediaRelocationHelper;

    private void ensureMediaHelper() {
        if (mediaRelocationHelper == null) {
            mediaRelocationHelper = new MediaRelocationHelper(thumbnailService, getMediaRoot());
        }
        if (mediaHandler == null) {
            mediaHandler = new MediaHandler(thumbnailService, getMediaRoot());
        }
    }

    // visible for testing
    String normalizeNumber(String number) {
        return contactResolver.normalizeNumber(number);
    }

    // visible for testing
    String guessExtension(String contentType, String name) {
        ensureMediaHelper();
        return mediaHandler.guessExtension(contentType, name);
    }
    /* visible for testing */ String computeDuplicateKeyForTest(Message msg) { return duplicateDetector.buildDuplicateKey(msg); }

    // Delegating methods for test compatibility (accessed via reflection)
    private String sanitizeContactName(String name) { return contactResolver.sanitizeContactName(name); }
    private boolean isGroupLikeName(String s) { return contactResolver.isGroupLikeName(s); }

    public static class ImportProgress {
        @Getter
        private final UUID id;
        @Getter
        private final long totalBytes;
        @JsonIgnore
        private final AtomicLong bytesRead = new AtomicLong(0);
        @JsonIgnore
        private final AtomicLong processedMessages = new AtomicLong(0);
        @JsonIgnore
        private final AtomicLong importedMessages = new AtomicLong(0);
        @JsonIgnore
        private final AtomicLong duplicateMessagesAtomic = new AtomicLong(0);
        private volatile int duplicateMessages;
        @Getter
        private volatile String status = "PENDING";
        @Getter
        private volatile String error;
        @Getter
        private volatile Instant startedAt;
        @Getter
        private volatile Instant finishedAt;
        public ImportProgress(UUID id, long totalBytes) { this.id=id; this.totalBytes=totalBytes; }

        public long getBytesRead(){return bytesRead.get();}
        public long getProcessedMessages(){return processedMessages.get();}
        public long getImportedMessages(){return importedMessages.get();}
        public long getDuplicateMessages(){return duplicateMessagesAtomic.get();}
        public int getDuplicateMessagesFinal(){return duplicateMessages;}

        public int getPercentBytes() {
            if (totalBytes <= 0) return 0;
            long current = bytesRead.get();
            if (current >= totalBytes) return 100;
            return (int) ((current * 100) / totalBytes);
        }

        void setStatus(String s){status=s;}
        void setError(String e){error=e;}
        void setStartedAt(Instant t){startedAt=t;}
        void setFinishedAt(Instant t){finishedAt=t;}
        void setBytesRead(long v){bytesRead.set(v);}
        void incProcessedMessages(){processedMessages.incrementAndGet();}
        void incImportedMessages(){importedMessages.incrementAndGet();}
        void incDuplicateMessages(){duplicateMessagesAtomic.incrementAndGet();}
        void setDuplicateMessages(int v){duplicateMessages=v;}
    }

    // ===== Batch helpers =====
    private void flushStreamingIfNeeded(List<Message> batch, ImportProgress progress) {
        if (batch.size() >= streamBatchSize) flushStreamingBatch(batch, progress);
    }
    private void flushStreamingBatch(List<Message> batch, ImportProgress progress) {
        if (batch.isEmpty()) return;
        try { 
            messageRepo.saveAll(batch); 
            batch.clear(); 
        }
        catch (org.springframework.dao.DataIntegrityViolationException dive) {
            // Duplicate key constraint violation - retry individually
            log.warn("Constraint violation during batch save (likely duplicates), retrying individually");
            int saved = 0;
            int skipped = 0;
            for (Message msg : batch) {
                try {
                    messageRepo.save(msg);
                    saved++;
                } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                    // Skip this duplicate message
                    log.debug("Skipped duplicate message: conversation={}, timestamp={}, direction={}", 
                        msg.getConversation().getId(), msg.getTimestamp(), msg.getDirection());
                    skipped++;
                    progress.incDuplicateMessages();
                } catch (Exception ex) {
                    log.error("Failed to save message", ex);
                    skipped++;
                }
            }
            log.info("Individual save complete: {} saved, {} skipped as duplicates", saved, skipped);
            batch.clear();
        }
        catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            // A duplicate message was found during the batch save
            // Try to save messages individually, skipping duplicates
            log.warn("Optimistic locking failure during batch save, retrying individually");
            int saved = 0;
            int skipped = 0;
            for (Message msg : batch) {
                try {
                    messageRepo.save(msg);
                    saved++;
                } catch (Exception ex) {
                    // Skip this message, it's likely a duplicate
                    log.debug("Skipped message during individual save: {}", ex.getMessage());
                    skipped++;
                }
            }
            log.info("Individual save complete: {} saved, {} skipped", saved, skipped);
            batch.clear();
        }
        catch (Exception e) { 
            log.error("Batch persist failed size={}", batch.size(), e); 
            progress.setStatus("FAILED"); 
            progress.setError("Persistence error: " + e.getMessage()); 
        }
    }
}
