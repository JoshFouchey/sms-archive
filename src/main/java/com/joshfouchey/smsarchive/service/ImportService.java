package com.joshfouchey.smsarchive.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.joshfouchey.smsarchive.model.*;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.io.input.CountingInputStream;
import java.util.concurrent.atomic.AtomicLong;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

@Slf4j
@Service
public class ImportService {

    private static final String SENDER_ME = "me";
    private static final String ADDR_TYPE_FROM = "137";
    private static final String ADDR_TYPE_TO = "151";
    private static final int MSG_BOX_INBOX = 1;
    private static final String TEXT_PLAIN = "text/plain";
    private static final String UNKNOWN_NORMALIZED = "__unknown__";
    private static final String APPLICATION_SMIL = "application/smil";
    private static final String UNKNOWN_NUMBER_DISPLAY = "unknown";

    // Metadata/XML attribute constants (restored)
    private static final String XML_ATTR_ADDRESS = "address";
    private static final String META_TEMP_SENDER = "_tempSender";
    private static final String META_TEMP_RECIPIENT = "_tempRecipient";
    private static final String META_TEMP_ADDRESS = "_tempAddress";
    private static final String META_NORMALIZED_NUMBER = "_normalizedNumber";
    private static final Set<String> GROUP_KEYWORDS = Set.of("group","team","vacation","chat");

    // Content-type mapping for file extensions
    private static final Map<String,String> CONTENT_TYPE_EXT_MAP = Map.ofEntries(
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/jpg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/gif", ".gif"),
            Map.entry("image/bmp", ".bmp"),
            Map.entry("image/heic", ".heic"),
            Map.entry("image/heif", ".heic"),
            Map.entry("video/mp4", ".mp4"),
            Map.entry("video/3gpp", ".3gp"),
            Map.entry("audio/mpeg", ".mp3"),
            Map.entry("audio/ogg", ".ogg"),
            Map.entry(TEXT_PLAIN, ".txt")
    );

    private final MessageRepository messageRepo;
    private final ContactRepository contactRepo;
    private final ThumbnailService thumbnailService;
    private final ConversationService conversationService; // new dependency
    private final UserRepository userRepository;
    private TaskExecutor importTaskExecutor; // executor for async jobs (optional)
    private final CurrentUserProvider currentUserProvider;
    // ThreadLocal to hold the user for async import tasks (SecurityContext not propagated)
    private final ThreadLocal<User> threadLocalImportUser = new ThreadLocal<>();

    // cache normalizedNumber -> Contact
    private final Map<String, Contact> contactCache = new ConcurrentHashMap<>();
    private final Map<UUID, ImportProgress> progressMap = new ConcurrentHashMap<>(); // job progress tracking

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
        // Absolute path helps confirm volume mounts
        log.info("Resolved media root: {}", Paths.get(mediaRoot).toAbsolutePath());
        mediaRelocationHelper = new MediaRelocationHelper(thumbnailService, getMediaRoot());
    }

    public ImportService(MessageRepository messageRepo, ContactRepository contactRepo,
                         CurrentUserProvider currentUserProvider, ThumbnailService thumbnailService,
                         ConversationService conversationService, UserRepository userRepository) {
        this.messageRepo = messageRepo;
        this.contactRepo = contactRepo;
        this.currentUserProvider = currentUserProvider;
        this.thumbnailService = thumbnailService;
        this.conversationService = conversationService;
        this.userRepository = userRepository;
    }

    // Remove multi-arg constructors added earlier
    // @Autowired optional setter for TaskExecutor
    @Autowired(required = false)
    public void setImportTaskExecutor(@Qualifier("importTaskExecutor") TaskExecutor executor) {
        this.importTaskExecutor = executor;
    }

    @VisibleForTesting
    Instant parseInstant(String millisStr) {
        if (StringUtils.isBlank(millisStr)) return Instant.EPOCH;
        String trimmed = millisStr.trim();
        if (NumberUtils.isDigits(trimmed)) {
            try {
                long ms = Long.parseLong(trimmed);
                if (trimmed.length() <= 10) return Instant.ofEpochSecond(ms); // seconds heuristic
                return Instant.ofEpochMilli(ms);
            } catch (NumberFormatException _) { /* fall through to ISO parse */ }
        }
        try { return Instant.parse(trimmed); } catch (DateTimeParseException ex) { log.warn("Unparseable timestamp '{}'", millisStr); return Instant.EPOCH; }
    }
    private int parseInt(String val, int def) {
        return NumberUtils.toInt(StringUtils.trimToEmpty(val), def);
    }
    private String nullIfBlank(String s) { return StringUtils.isBlank(s) ? null : s; }

    // ===== Streaming Import (Large XML) =====
    @CacheEvict(value = "analyticsDashboard", allEntries = true)
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
    @CacheEvict(value = "analyticsDashboard", allEntries = true)
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

    public ImportProgress getProgress(UUID id) { return progressMap.get(id); }

    // Utility helpers
    private String attr(XMLStreamReader r, String name) { return r.getAttributeValue(null, name); }

    private XMLStreamReader createSecureXmlStreamReader(CountingInputStream cis) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        try {
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
        } catch (IllegalArgumentException _) {
            // Ignored: Property not supported by the factory
        }
        try {
            factory.setProperty("javax.xml.stream.supportDTD", Boolean.FALSE);
        } catch (IllegalArgumentException _) {
            // Ignored: Property not supported by the factory
        }
        try {
            factory.setProperty("javax.xml.stream.isReplacingEntityReferences", Boolean.TRUE);
        } catch (IllegalArgumentException _) {
            // Ignored: Property not supported by the factory
        }
        return factory.createXMLStreamReader(cis);
    }

    @Getter
    private static class ElementContext {
        Message cur;
        List<MessagePart> curParts;
        List<Map<String,Object>> curMedia;
        StringBuilder textAgg;
        String suggestedName;
        boolean inMultipart;
        Set<String> participantNumbers; // normalized participants for multipart/group
        String threadKey; // external address for group threads
        ElementContext(Message c, List<MessagePart> parts, List<Map<String,Object>> media, StringBuilder agg, String name, boolean multi, Set<String> participants, String thread) {
            this.cur = c;
            this.curParts = parts;
            this.curMedia = media;
            this.textAgg = agg;
            this.suggestedName = name;
            this.inMultipart = multi;
            this.participantNumbers = participants;
            this.threadKey = thread;
        }
    }

    private void handleSms(Message cur, String suggestedName, ImportProgress progress, Set<String> seenKeys, List<Message> batch) {
        finalizeStreamingContact(cur, suggestedName);
        assignConversationForSms(cur, suggestedName); // Assign conversation after contact is resolved
        boolean dup = isDuplicateInRunOrDb(cur, seenKeys, batch);
        if (dup) {
            progress.incDuplicateMessages();
        } else {
            batch.add(cur);
            progress.incImportedMessages();
        }
        progress.incProcessedMessages();
        flushStreamingIfNeeded(batch, progress);
    }

    protected void runStreamingImportAsync(UUID jobId, Path xmlPath) {
        ImportProgress progress = progressMap.get(jobId);
        if (progress == null) { log.warn("No progress entry for job {}", jobId); return; }
        progress.setStatus("RUNNING");
        progress.setStartedAt(Instant.now());
        List<Message> batch = new ArrayList<>(streamBatchSize);
        Set<String> seenKeys = new HashSet<>();
        try (CountingInputStream cis = new CountingInputStream(Files.newInputStream(xmlPath))) {
            XMLStreamReader r = createSecureXmlStreamReader(cis);
            Message cur = null;
            List<MessagePart> curParts = null;
            List<Map<String,Object>> curMedia = null;
            StringBuilder textAgg = null;
            String suggestedName = null;
            boolean inMultipart = false;
            Set<String> participantNumbers = null;
            String threadKey = null;
            while (r.hasNext()) {
                int evt = r.next();
                if (evt == XMLStreamConstants.START_ELEMENT) {
                    ElementContext ctx = new ElementContext(cur, curParts, curMedia, textAgg, suggestedName, inMultipart, participantNumbers, threadKey);
                    handleStartElement(r, ctx, progress, seenKeys, batch);
                    cur = ctx.cur;
                    curParts = ctx.curParts;
                    curMedia = ctx.curMedia;
                    textAgg = ctx.textAgg;
                    suggestedName = ctx.suggestedName;
                    inMultipart = ctx.inMultipart;
                    participantNumbers = ctx.participantNumbers;
                    threadKey = ctx.threadKey;
                } else if (evt == XMLStreamConstants.END_ELEMENT) {
                    ElementContext ctx = new ElementContext(cur, curParts, curMedia, textAgg, suggestedName, inMultipart, participantNumbers, threadKey);
                    handleEndElement(r, ctx, progress, seenKeys, batch);
                    cur = ctx.cur;
                    curParts = ctx.curParts;
                    curMedia = ctx.curMedia;
                    textAgg = ctx.textAgg;
                    suggestedName = ctx.suggestedName;
                    inMultipart = ctx.inMultipart;
                    participantNumbers = ctx.participantNumbers;
                    threadKey = ctx.threadKey;
                }
                progress.setBytesRead(cis.getByteCount());
            }
            flushStreamingBatch(batch, progress);
            r.close();
            progress.setDuplicateMessages((int) progress.getDuplicateMessages());
            progress.setStatus("COMPLETED");
            progress.setFinishedAt(Instant.now());
            log.info("Streaming import {} completed: imported={}, duplicates={}", jobId, progress.getImportedMessages(), progress.getDuplicateMessages());
        } catch (Exception e) {
            log.error("Streaming import {} failed", jobId, e);
            progress.setStatus("FAILED");
            progress.setError(e.getMessage());
            progress.setFinishedAt(Instant.now());
        }
    }

    private void handleStartElement(XMLStreamReader r,
                                    ElementContext ctx,
                                    ImportProgress progress,
                                    Set<String> seenKeys,
                                    List<Message> batch) {
        String local = r.getLocalName();
        switch (local) {
            case "sms" -> handleStartSms(r, ctx, progress, seenKeys, batch);
            case "mms", "rcs" -> startMultipartMessage(r, ctx, local);
            case "part" -> handleMultipartPart(r, ctx);
            case "addr" -> processAddrElement(r, ctx);
            default -> { /* no-op */ }
        }
    }

    // Extracted from handleStartElement for readability
    private void handleStartSms(XMLStreamReader r, ElementContext ctx, ImportProgress progress, Set<String> seenKeys, List<Message> batch) {
        ctx.suggestedName = nullIfBlank(attr(r, "contact_name"));
        ctx.cur = buildSmsStreaming(r);
        handleSms(ctx.cur, ctx.suggestedName, progress, seenKeys, batch);
        ctx.cur = null; ctx.suggestedName = null;
    }
    private void startMultipartMessage(XMLStreamReader r, ElementContext ctx, String local) {
        ctx.suggestedName = nullIfBlank(attr(r, "contact_name"));
        ctx.cur = buildMultipartHeaderStreaming(r, local.equals("mms") ? MessageProtocol.MMS : MessageProtocol.RCS);
        ctx.threadKey = nullIfBlank(attr(r, XML_ATTR_ADDRESS));
        startMultipart();
        ctx.curParts = new ArrayList<>();
        ctx.curMedia = new ArrayList<>();
        ctx.textAgg = new StringBuilder();
        ctx.inMultipart = true;
        ctx.participantNumbers = new LinkedHashSet<>();
    }
    private void handleMultipartPart(XMLStreamReader r, ElementContext ctx) {
        if (ctx.inMultipart && ctx.cur != null) {
            handlePart(r, ctx.cur, ctx.curParts, ctx.curMedia, ctx.textAgg);
        }
    }

    // Address element processing extracted
    private void processAddrElement(XMLStreamReader r, ElementContext ctx) {
        if (!(ctx.inMultipart && ctx.cur != null)) return;
        accumulateAddressStreaming(r, ctx.cur);
        String addrVal = attr(r, XML_ATTR_ADDRESS);
        String addrType = attr(r, "type");
        if (!shouldConsiderAddrForParticipants(ctx.cur, addrType)) return;
        if (addrVal == null || addrVal.isBlank() || addrVal.equalsIgnoreCase(SENDER_ME)) return;
        if (ctx.participantNumbers == null) ctx.participantNumbers = new LinkedHashSet<>();
        String norm = normalizeNumber(addrVal);
        if (!UNKNOWN_NORMALIZED.equals(norm)) ctx.participantNumbers.add(norm);
    }
    private boolean shouldConsiderAddrForParticipants(Message cur, String addrType) {
        if (cur.getDirection() == MessageDirection.INBOUND) {
            return ADDR_TYPE_FROM.equals(addrType) || "130".equals(addrType); // include sender + other participants
        }
        if (cur.getDirection() == MessageDirection.OUTBOUND) {
            return ADDR_TYPE_TO.equals(addrType); // outbound recipients
        }
        return false;
    }

    // Simplified conversation assignment for SMS
    private void assignConversationForSms(Message msg, String suggestedName) {
        String normalized = resolveNormalizedNumberForSms(msg);
        if (normalized == null || normalized.isBlank() || UNKNOWN_NORMALIZED.equals(normalized)) {
            log.debug("Skipping conversation assignment - normalized invalid: {}", normalized);
            cleanupNormalizedMetadata(msg); // ensure cleanup even when skipping
            return;
        }
        User user = resolveImportUser();
        Conversation convo = conversationService.findOrCreateOneToOneForUser(user, normalized, suggestedName);
        if (isInvalidConversation(convo)) {
            log.error("Failed to create/find conversation for normalized number: {}", normalized);
            cleanupNormalizedMetadata(msg);
            return;
        }
        updateConversationLastMessage(convo, msg);
        msg.setConversation(convo);
        log.debug("Assigned message to conversation ID: {}", convo.getId());
        cleanupNormalizedMetadata(msg);
    }
    private String resolveNormalizedNumberForSms(Message msg) {
        if (msg.getSenderContact() != null) {
            return msg.getSenderContact().getNormalizedNumber();
        }
        Map<String,Object> meta = msg.getMetadata();
        if (meta != null && meta.containsKey(META_NORMALIZED_NUMBER)) {
            return (String) meta.get(META_NORMALIZED_NUMBER);
        }
        return null;
    }
    private void cleanupNormalizedMetadata(Message msg) {
        Map<String,Object> meta = msg.getMetadata();
        if (meta == null) return;
        meta.remove(META_NORMALIZED_NUMBER);
        if (meta.isEmpty()) msg.setMetadata(null);
    }

    private void assignConversationForMultipart(Message msg, String threadKey, Set<String> participantNumbers, String suggestedName) {
        if (participantNumbers == null || participantNumbers.isEmpty()) {
            log.debug("Skipping conversation assignment - no participant numbers");
            return;
        }
        if ((threadKey == null || threadKey.isBlank()) && participantNumbers.size() > 1) {
            threadKey = buildSyntheticThreadKey(participantNumbers);
        }
        User user = resolveImportUser();
        Conversation convo = (participantNumbers.size() == 1)
                ? conversationService.findOrCreateOneToOneForUser(user, participantNumbers.iterator().next(), suggestedName)
                : conversationService.findOrCreateGroupForUser(user, threadKey, participantNumbers, suggestedName);
        if (isInvalidConversation(convo)) {
            log.error("Failed to create/find conversation for participants: {}", participantNumbers);
            return;
        }
        updateConversationLastMessage(convo, msg);
        msg.setConversation(convo);
        log.debug("Assigned multipart message to conversation ID: {}", convo.getId());
    }
    private boolean isInvalidConversation(Conversation convo) { return convo == null || convo.getId() == null; }
    private void updateConversationLastMessage(Conversation convo, Message msg) {
        if (convo.getLastMessageAt() == null || msg.getTimestamp().isAfter(convo.getLastMessageAt())) {
            convo.setLastMessageAt(msg.getTimestamp());
            conversationService.save(convo);
        }
    }
    private User resolveImportUser() {
        User user = threadLocalImportUser.get();
        return (user != null) ? user : currentUserProvider.getCurrentUser();
    }

    private void finalizeGroupMessageContact(Message msg) {
        Map<String, Object> meta = msg.getMetadata();
        String tempSender = meta != null ? (String) meta.get(META_TEMP_SENDER) : null;
        String tempAddress = meta != null ? (String) meta.get(META_TEMP_ADDRESS) : null;
        User user = resolveImportUser();
        msg.setUser(user);
        String senderAddress = (msg.getDirection() == MessageDirection.INBOUND) ? (tempSender != null ? tempSender : tempAddress) : null;
        log.debug("finalizeGroupMessageContact - direction: {}, senderAddress: {}", msg.getDirection(), senderAddress);
        if (senderAddress != null && !senderAddress.isBlank()) {
            Contact sender = resolveContact(user, senderAddress, null);
            msg.setSenderContact(sender);
            log.debug("finalizeGroupMessageContact - resolved sender: id={}, number={}, normalized={}",
                    sender != null ? sender.getId() : null,
                    sender != null ? sender.getNumber() : null,
                    sender != null ? sender.getNormalizedNumber() : null);
        } else {
            msg.setSenderContact(null);
        }
        cleanupTempMetadata(meta);
    }

    private void finalizeStreamingContact(Message msg, String suggestedName) {
        Map<String, Object> meta = msg.getMetadata();
        String tempSender = meta != null ? (String) meta.get(META_TEMP_SENDER) : null;
        String tempRecipient = meta != null ? (String) meta.get(META_TEMP_RECIPIENT) : null;
        String tempAddress = meta != null ? (String) meta.get(META_TEMP_ADDRESS) : null;
        log.debug("finalizeStreamingContact - protocol: {}, direction: {}, tempSender: {}, tempRecipient: {}, tempAddress: {}",
                msg.getProtocol(), msg.getDirection(), tempSender, tempRecipient, tempAddress);
        String counterparty;
        String senderAddress;
        if (msg.getDirection() == MessageDirection.INBOUND) {
            counterparty = tempSender != null ? tempSender : tempAddress;
            senderAddress = counterparty;
        } else {
            counterparty = tempRecipient != null ? tempRecipient : tempAddress;
            counterparty = pickFirstNonMe(counterparty);
            senderAddress = null;
        }
        User user = resolveImportUser();
        msg.setUser(user);
        log.debug("finalizeStreamingContact - counterparty: {}, suggestedName: {}", counterparty, suggestedName);
        Contact contact = resolveContact(user, counterparty, suggestedName);
        log.debug("finalizeStreamingContact - resolved contact: id={}, number={}, normalized={}",
                contact != null ? contact.getId() : null,
                contact != null ? contact.getNumber() : null,
                contact != null ? contact.getNormalizedNumber() : null);
        if (msg.getDirection() == MessageDirection.INBOUND && senderAddress != null) {
            msg.setSenderContact(contact);
        } else {
            msg.setSenderContact(null);
        }
        if (contact != null && meta != null) {
            meta.put(META_NORMALIZED_NUMBER, contact.getNormalizedNumber());
        }
        cleanupTempMetadata(meta); // retain normalized until conversation assignment
    }
    private String pickFirstNonMe(String recipients) {
        if (recipients == null) return null;
        return Arrays.stream(recipients.split(","))
                .map(String::trim)
                .filter(s -> !s.equalsIgnoreCase(SENDER_ME) && !s.isBlank())
                .findFirst()
                .orElse(recipients);
    }
    private void cleanupTempMetadata(Map<String,Object> meta) {
        if (meta == null) return;
        meta.remove(META_TEMP_SENDER);
        meta.remove(META_TEMP_RECIPIENT);
        meta.remove(META_TEMP_ADDRESS);
        // Do not remove normalized number here
        if (meta.isEmpty()) { /* leave empty map to be cleaned later */ }
    }

    private void accumulateAddressStreaming(XMLStreamReader r, Message msg) {
        String type = r.getAttributeValue(null, "type");
        String address = r.getAttributeValue(null, XML_ATTR_ADDRESS);
        if (type == null || address == null) return;
        Map<String, Object> meta = ensureMetadata(msg);
        if (ADDR_TYPE_FROM.equals(type)) {
            meta.put(META_TEMP_SENDER, address);
        } else if (ADDR_TYPE_TO.equals(type)) {
            appendRecipient(meta, address);
        }
    }
    private Map<String,Object> ensureMetadata(Message msg) {
        Map<String,Object> meta = msg.getMetadata();
        if (meta == null) { meta = new HashMap<>(); msg.setMetadata(meta); }
        return meta;
    }
    private void appendRecipient(Map<String,Object> meta, String address) {
        String existing = (String) meta.get(META_TEMP_RECIPIENT);
        if (existing == null || existing.isBlank()) meta.put(META_TEMP_RECIPIENT, address);
        else meta.put(META_TEMP_RECIPIENT, existing + "," + address);
    }

    private MediaRelocationHelper mediaRelocationHelper; // helper for media relocation

    private void ensureMediaHelper() { // lazy init for test contexts without @PostConstruct
        if (mediaRelocationHelper == null) {
            mediaRelocationHelper = new MediaRelocationHelper(thumbnailService, getMediaRoot());
        }
    }

    private boolean isGroupLikeName(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.split("\\s+").length < 2) return false; // require >=2 words
        for (String kw : GROUP_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    @VisibleForTesting
    String normalizeNumber(String number) {
        if (number == null || number.isBlank()) {
            return UNKNOWN_NORMALIZED;
        }
        String digits = number.replaceAll("\\D", "");
        if (digits.isEmpty()) return UNKNOWN_NORMALIZED;
        // NANP canonicalization: always 11 digits with leading 1
        if (digits.length() == 10) {
            return "1" + digits; // add leading country code
        }
        if (digits.length() == 11 && digits.startsWith("1")) {
            return digits; // already canonical
        }
        return digits; // fallback for non-NANP or other lengths
    }

    @VisibleForTesting
    String guessExtension(String contentType, String name) {
        if (StringUtils.isNotBlank(name) && name.contains(".")) {
            String ext = StringUtils.substringAfterLast(name, ".");
            if (ext.length() <= 6) return "." + ext.toLowerCase();
        }
        if (StringUtils.isBlank(contentType)) return ".bin";
        String lower = contentType.toLowerCase();
        return CONTENT_TYPE_EXT_MAP.getOrDefault(lower, ".bin");
    }
    @VisibleForTesting String computeDuplicateKeyForTest(Message msg) { return buildDuplicateKey(msg); }

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

    // ===== Duplicate & batch helpers (restored) =====
    private boolean isDuplicateInRunOrDb(Message msg, Set<String> seenKeys, List<Message> currentBatch) {
        String key = buildDuplicateKey(msg);
        // Check in-memory tracking first (fastest)
        if (seenKeys.contains(key)) {
            log.debug("Duplicate found in seenKeys: {}", key);
            return true;
        }
        // Check current batch (before it's persisted)
        if (isDuplicateInBatch(msg, currentBatch)) {
            log.debug("Duplicate found in current batch: {}", key);
            return true;
        }
        // Check database (most expensive)
        boolean dbDup = isDuplicate(msg);
        if (dbDup) {
            log.debug("Duplicate found in database: {}", key);
        }
        if (!dbDup) seenKeys.add(key);
        return dbDup;
    }

    private boolean isDuplicateInBatch(Message msg, List<Message> batch) {
        if (batch == null || batch.isEmpty()) return false;
        String bodyNorm = msg.getBody() == null ? "" : msg.getBody().trim().toLowerCase();
        for (Message existing : batch) {
            if (areMessagesDuplicate(msg, existing, bodyNorm)) {
                return true;
            }
        }
        return false;
    }

    private boolean areMessagesDuplicate(Message msg1, Message msg2, String body1Normalized) {
        // Compare timestamps
        if (!msg1.getTimestamp().equals(msg2.getTimestamp())) {
            return false;
        }
        // Compare bodies (case-insensitive, trimmed)
        String body2Norm = msg2.getBody() == null ? "" : msg2.getBody().trim().toLowerCase();
        if (!body1Normalized.equals(body2Norm)) {
            return false;
        }
        // Compare user
        UUID userId1 = msg1.getUser() != null ? msg1.getUser().getId() : null;
        UUID userId2 = msg2.getUser() != null ? msg2.getUser().getId() : null;
        if (userId1 != null && userId2 != null && !userId1.equals(userId2)) {
            return false;
        }
        // Compare conversation if both are set
        Long convId1 = msg1.getConversation() != null ? msg1.getConversation().getId() : null;
        Long convId2 = msg2.getConversation() != null ? msg2.getConversation().getId() : null;
        if (convId1 != null && convId2 != null && !convId1.equals(convId2)) {
            return false;
        }
        // Compare direction/msgBox
        if (msg1.getMsgBox() != msg2.getMsgBox()) {
            return false;
        }
        // Compare protocol
        if (msg1.getProtocol() != msg2.getProtocol()) {
            return false;
        }
        return true;
    }

    private String buildDuplicateKey(Message msg) {
        String bodyNorm = msg.getBody() == null ? "" : msg.getBody().trim().toLowerCase();
        Long conversationId = msg.getConversation() == null ? null : msg.getConversation().getId();
        String conversationStr = (conversationId == null) ? "null" : conversationId.toString();
        UUID userId = msg.getUser() != null ? msg.getUser().getId() : null;
        String userStr = (userId == null) ? "null" : userId.toString();
        return userStr + "|" + conversationStr + "|" + msg.getTimestamp() + "|" + msg.getMsgBox() + "|" + msg.getProtocol() + "|" + bodyNorm;
    }
    private void flushStreamingIfNeeded(List<Message> batch, ImportProgress progress) {
        if (batch.size() >= streamBatchSize) flushStreamingBatch(batch, progress);
    }
    private void flushStreamingBatch(List<Message> batch, ImportProgress progress) {
        if (batch.isEmpty()) return;
        try { messageRepo.saveAll(batch); batch.clear(); }
        catch (Exception e) { log.error("Batch persist failed size={}", batch.size(), e); progress.setStatus("FAILED"); progress.setError("Persistence error: " + e.getMessage()); }
    }

    private boolean isDuplicate(Message msg) {
        try {
            String normalizedBody = msg.getBody() == null ? null : msg.getBody().trim().toLowerCase();
            User user = msg.getUser();
            if (user == null) {
                log.warn("Message has no user set, cannot check for duplicates properly");
                return false;
            }
            if (msg.getConversation() != null && msg.getConversation().getId() != null) {
                return messageRepo.existsByConversationAndTimestampAndBody(
                    msg.getConversation(),
                    msg.getTimestamp(),
                    normalizedBody,
                    msg.getMsgBox(),
                    msg.getProtocol(),
                    user
                );
            }
            return messageRepo.existsByTimestampAndBody(
                msg.getTimestamp(),
                normalizedBody,
                msg.getMsgBox(),
                msg.getProtocol(),
                user
            );
        } catch (Exception e) {
            log.warn("Duplicate check failed, proceeding to insert message: {}", e.getMessage());
            return false;
        }
    }

    // ===== Message building (restored) =====
    private Message buildSmsStreaming(XMLStreamReader r) {
        Message msg = new Message();
        msg.setProtocol(MessageProtocol.SMS);
        msg.setTimestamp(parseInstant(r.getAttributeValue(null, "date")));
        msg.setBody(r.getAttributeValue(null, "body"));
        int box = parseInt(r.getAttributeValue(null, "type"), 0);
        msg.setMsgBox(box);
        msg.setDirection(box == MSG_BOX_INBOX ? MessageDirection.INBOUND : MessageDirection.OUTBOUND);
        String address = r.getAttributeValue(null, XML_ATTR_ADDRESS);
        if (address != null) msg.setMetadata(new HashMap<>(Map.of(META_TEMP_ADDRESS, address)));
        return msg;
    }
    private Message buildMultipartHeaderStreaming(XMLStreamReader r, MessageProtocol protocol) {
        Message msg = new Message();
        msg.setProtocol(protocol);
        msg.setTimestamp(parseInstant(r.getAttributeValue(null, "date")));
        int box = parseInt(r.getAttributeValue(null, "msg_box"), 0);
        msg.setMsgBox(box);
        msg.setDirection(box == MSG_BOX_INBOX ? MessageDirection.INBOUND : MessageDirection.OUTBOUND);
        if (protocol == MessageProtocol.RCS) {
            String bodyAttr = nullIfBlank(r.getAttributeValue(null, "body"));
            if (bodyAttr != null) msg.setBody(bodyAttr);
        }
        return msg;
    }

    private void handlePart(XMLStreamReader r, Message cur, List<MessagePart> curParts, List<Map<String,Object>> curMedia, StringBuilder textAgg) {
        MessagePart part = buildPartStreaming(r, cur, curParts.size());
        curParts.add(part);
        String ct = Optional.ofNullable(part.getContentType()).orElse("");
        if (part.getText() != null && ct.startsWith(TEXT_PLAIN)) textAgg.append(part.getText()).append(' ');
        if (!ct.equalsIgnoreCase(TEXT_PLAIN) && !ct.equalsIgnoreCase(APPLICATION_SMIL)) {
            Map<String,Object> mediaMap = new LinkedHashMap<>();
            mediaMap.put("seq", part.getSeq());
            mediaMap.put("contentType", ct);
            mediaMap.put("name", Optional.ofNullable(part.getName()).orElse(""));
            String fp = part.getFilePath();
            if (fp == null) {
                log.warn("Media part seq={} skipped (no filePath, invalid/missing Base64)", part.getSeq());
                mediaMap.put("filePath", null);
                curMedia.add(mediaMap);
            } else {
                Path mediaPath = Paths.get(fp).normalize();
                mediaMap.put("filePath", mediaPath.toString());
                curMedia.add(mediaMap);
                ensureThumbnail(mediaPath, ct, true);
            }
        }
    }

    private Optional<String> saveMediaPart(String base64, MessagePart part) {
        try {
            if (!isValidBase64(base64)) { log.error("Invalid Base64 input: {}", base64); return Optional.empty(); }
            Message message = part.getMessage();
            String conversationDirName = (message.getConversation() != null && message.getConversation().getId() != null)
                    ? message.getConversation().getId().toString() : "_noconversation";
            Path dir = getMediaRoot().resolve(conversationDirName);
            Files.createDirectories(dir);
            byte[] dataBytes = Base64.getDecoder().decode(base64);
            String ext = guessExtension(part.getContentType(), part.getName());
            Path original = MediaFileNamer.buildUniqueMediaPath(dir, message.getId(), part.getSeq(), message.getTimestamp(), dataBytes, ext);
            Files.write(original, dataBytes);
            ensureThumbnail(original, part.getContentType(), false);
            part.setFilePath(original.toString());
            safeSetSize(part, original);
            return Optional.of(original.toString());
        } catch (Exception e) { log.error("Media save failed", e); return Optional.empty(); }
    }
    private void ensureThumbnail(Path original, String contentType, boolean allowExisting) {
        try {
            Path thumb = thumbnailService.deriveStemThumbnail(original);
            if (Files.exists(thumb) && allowExisting) return; // already present
            thumbnailService.createThumbnail(original, thumb, contentType, true);
        } catch (Exception ex) {
            log.warn("Thumbnail generation failed for {}", original, ex);
        }
    }
    // Added helper to set size with explanation
    private void safeSetSize(MessagePart part, Path path) {
        try {
            if (Files.exists(path)) {
                part.setSizeBytes(Files.size(path));
            }
        } catch (Exception ex) {
            // Size is best-effort; failures (e.g. permissions) are non-fatal.
            log.debug("Unable to determine size for media part at {}: {}", path, ex.getMessage());
        }
    }

    private MessagePart buildPartStreaming(XMLStreamReader r, Message msg, int idx) {
        MessagePart part = new MessagePart();
        part.setMessage(msg);
        part.setSeq(parseInt(r.getAttributeValue(null, "seq"), idx));
        part.setContentType(r.getAttributeValue(null, "ct"));
        part.setName(nullIfBlank(r.getAttributeValue(null, "name")));
        part.setText(nullIfBlank(r.getAttributeValue(null, "text")));
        String data = r.getAttributeValue(null, "data");
        if (data != null && !data.isBlank()) {
            saveMediaPart(data, part).ifPresent(part::setFilePath);
            if (part.getFilePath() != null) {
                safeSetSize(part, Path.of(part.getFilePath())); // replaced empty catch
            }
        }
        return part;
    }

    // ===== Contact resolution & name sanitization (restored) =====
    private Contact resolveContact(User user, String number, String suggestedName) {
        String normalized = normalizeNumber(number);
        String cacheKey = user.getId() + "|" + normalized;
        Contact cached = contactCache.get(cacheKey);
        if (cached != null) {
            if (shouldUpdateContactName(cached, suggestedName)) {
                String sanitized = sanitizeContactName(suggestedName);
                cached.setName(sanitized);
                cached = contactRepo.save(cached);
                contactCache.put(cacheKey, cached);
                log.debug("Updated contact {} name to '{}'", cached.getId(), sanitized);
            }
            return cached;
        }
        // Find or create contact with retry on duplicate key violation
        Contact contact = contactRepo.findByUserAndNormalizedNumber(user, normalized).orElseGet(() -> {
            try {
                Contact c = new Contact();
                c.setUser(user);
                c.setNumber(number == null ? UNKNOWN_NUMBER_DISPLAY : number);
                c.setNormalizedNumber(normalized);
                String sanitizedName = sanitizeContactName(suggestedName);
                c.setName(sanitizedName != null ? sanitizedName : c.getNumber());
                return contactRepo.save(c);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Contact was created by another thread/transaction, fetch it again
                return contactRepo.findByUserAndNormalizedNumber(user, normalized)
                        .orElseThrow(() -> new RuntimeException("Failed to find or create contact for: " + normalized));
            }
        });
        if (shouldUpdateContactName(contact, suggestedName)) {
            String sanitized = sanitizeContactName(suggestedName);
            contact.setName(sanitized);
            contact = contactRepo.save(contact);
        }
        contactCache.put(cacheKey, contact);
        return contact;
    }
    private boolean shouldUpdateContactName(Contact contact, String suggestedName) {
        String sanitizedSuggested = sanitizeContactName(suggestedName);
        if (sanitizedSuggested == null) return false;
        String currentName = contact.getName();
        if (currentName == null || currentName.isBlank()) return true;
        if (currentName.equalsIgnoreCase(UNKNOWN_NUMBER_DISPLAY)) return true;
        String currentNameNormalized = normalizeNumber(currentName);
        return currentNameNormalized.equals(contact.getNormalizedNumber());
    }
    private String sanitizeContactName(String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        if (trimmed.equalsIgnoreCase(UNKNOWN_NUMBER_DISPLAY) ||
            trimmed.equalsIgnoreCase("(" + UNKNOWN_NUMBER_DISPLAY + ")") ||
            trimmed.matches("^\\(.*" + UNKNOWN_NUMBER_DISPLAY + ".*\\)$")) return null;
        if (isGroupLikeName(trimmed)) return null; // group labels not used for contact names
        return trimmed;
    }
    // isGroupLikeName already defined earlier (kept refactored version)

    // Added missing end-element handler (restored after refactor)
    private void handleEndElement(XMLStreamReader r,
                                  ElementContext ctx,
                                  ImportProgress progress,
                                  Set<String> seenKeys,
                                  List<Message> batch) {
        String local = r.getLocalName();
        if (ctx.inMultipart && ctx.cur != null && ("mms".equals(local) || "rcs".equals(local))) {
            finalizeMultipart(ctx.cur, ctx.suggestedName, ctx.curParts, ctx.curMedia, ctx.textAgg, ctx.participantNumbers);
            assignConversationForMultipart(ctx.cur, ctx.threadKey, ctx.participantNumbers, ctx.suggestedName);
            boolean dup = isDuplicateInRunOrDb(ctx.cur, seenKeys, batch);
            if (dup) { progress.incDuplicateMessages(); }
            else { batch.add(ctx.cur); progress.incImportedMessages(); }
            progress.incProcessedMessages();
            flushStreamingIfNeeded(batch, progress);
            // Reset context
            ctx.cur = null;
            ctx.curParts = null;
            ctx.curMedia = null;
            ctx.textAgg = null;
            ctx.inMultipart = false;
            ctx.suggestedName = null;
            ctx.participantNumbers = null;
            ctx.threadKey = null;
        }
    }

    private void finalizeMultipart(Message cur,
                                   String suggestedName,
                                   List<MessagePart> curParts,
                                   List<Map<String,Object>> curMedia,
                                   StringBuilder textAgg,
                                   Set<String> participantNumbers) {
        if (cur == null) return;
        if (StringUtils.isBlank(cur.getBody()) && textAgg != null && textAgg.length() > 0) {
            cur.setBody(textAgg.toString().trim());
        }
        if (curMedia != null && !curMedia.isEmpty()) {
            cur.setMedia(Map.of("parts", curMedia));
        }
        cur.setParts(Objects.requireNonNullElse(curParts, Collections.emptyList()));
        boolean isGroup = participantNumbers != null && participantNumbers.size() > 1;
        if (isGroup) {
            finalizeGroupMessageContact(cur);
        } else {
            finalizeStreamingContact(cur, suggestedName);
        }
        ensureMediaHelper();
        mediaRelocationHelper.relocate(cur);
    }

    private String buildSyntheticThreadKey(Set<String> participantNumbers) {
        List<String> sorted = new ArrayList<>(participantNumbers);
        Collections.sort(sorted);
        return "GROUP:" + String.join(";", sorted);
    }

    private boolean isValidBase64(String base64) { // restored helper
        try { Base64.getDecoder().decode(base64); return true; } catch (IllegalArgumentException _) { return false; }
    }

    private void startMultipart() {
        // placeholder hook: could record metrics or initialize multipart state in future
    }
}
