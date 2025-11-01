package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.*;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
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
import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
@Service
public class ImportService {

    private boolean postgresDialect; // set via DataSource detection

    private static final String SENDER_ME = "me";
    private static final String ADDR_TYPE_FROM = "137";
    private static final String ADDR_TYPE_TO = "151";
    private static final int MSG_BOX_INBOX = 1;
    private static final int MSG_BOX_SENT = 2;
    private static final String TEXT_PLAIN = "text/plain";
    private static final String UNKNOWN_NORMALIZED = "__unknown__";
    private static final String APPLICATION_SMIL = "application/smil";
    private static final String UNKNOWN_NUMBER_DISPLAY = "unknown";

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
    }

    public ImportService(MessageRepository messageRepo, ContactRepository contactRepo,
                         CurrentUserProvider currentUserProvider, ThumbnailService thumbnailService) {
        this.messageRepo = messageRepo;
        this.contactRepo = contactRepo;
        this.currentUserProvider = currentUserProvider;
        this.thumbnailService = thumbnailService;
    }

    // Remove multi-arg constructors added earlier
    // @Autowired optional setter for TaskExecutor
    @Autowired(required = false)
    public void setImportTaskExecutor(@Qualifier("importTaskExecutor") TaskExecutor executor) {
        this.importTaskExecutor = executor;
    }

    @Autowired
    public void setDataSource(DataSource ds) {
        try (Connection connection = ds.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            this.postgresDialect = product != null && product.toLowerCase().contains("postgres");
        } catch (SQLException e) {
            log.warn("Failed to detect database dialect", e);
            this.postgresDialect = false;
        }
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
            } catch (NumberFormatException ignored) { /* fall through to ISO parse */ }
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
    public ImportProgress getProgress(UUID id) { return progressMap.get(id); }

    // Utility helpers
    private String attr(XMLStreamReader r, String name) { return r.getAttributeValue(null, name); }

    private XMLStreamReader createSecureXmlStreamReader(CountingInputStream cis) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        try {
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
        } catch (IllegalArgumentException e) {
            // Ignored: Property not supported by the factory
        }
        try {
            factory.setProperty("javax.xml.stream.supportDTD", Boolean.FALSE);
        } catch (IllegalArgumentException e) {
            // Ignored: Property not supported by the factory
        }
        try {
            factory.setProperty("javax.xml.stream.isReplacingEntityReferences", Boolean.TRUE);
        } catch (IllegalArgumentException e) {
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
        ElementContext(Message c, List<MessagePart> parts, List<Map<String,Object>> media, StringBuilder agg, String name, boolean multi) {
            this.cur = c;
            this.curParts = parts;
            this.curMedia = media;
            this.textAgg = agg;
            this.suggestedName = name;
            this.inMultipart = multi;
        }
    }

    private void handleSms(Message cur, String suggestedName, ImportProgress progress, Set<String> seenKeys, List<Message> batch) {
        finalizeStreamingContact(cur, suggestedName);
        boolean dup = isDuplicateInRunOrDb(cur, seenKeys);
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
            while (r.hasNext()) {
                int evt = r.next();
                if (evt == XMLStreamConstants.START_ELEMENT) {
                    ElementContext ctx = new ElementContext(cur, curParts, curMedia, textAgg, suggestedName, inMultipart);
                    ctx = handleStartElement(r, ctx, progress, seenKeys, batch);
                    cur = ctx.cur;
                    curParts = ctx.curParts;
                    curMedia = ctx.curMedia;
                    textAgg = ctx.textAgg;
                    suggestedName = ctx.suggestedName;
                    inMultipart = ctx.inMultipart;
                } else if (evt == XMLStreamConstants.END_ELEMENT) {
                    ElementContext ctx = new ElementContext(cur, curParts, curMedia, textAgg, suggestedName, inMultipart);
                    ctx = handleEndElement(r, ctx, progress, seenKeys, batch);
                    cur = ctx.cur;
                    curParts = ctx.curParts;
                    curMedia = ctx.curMedia;
                    textAgg = ctx.textAgg;
                    suggestedName = ctx.suggestedName;
                    inMultipart = ctx.inMultipart;
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

    private ElementContext handleStartElement(XMLStreamReader r,
                                              ElementContext ctx,
                                              ImportProgress progress,
                                              Set<String> seenKeys,
                                              List<Message> batch) {
        String local = r.getLocalName();
        switch (local) {
            case "sms" -> {
                ctx.suggestedName = nullIfBlank(attr(r, "contact_name"));
                ctx.cur = buildSmsStreaming(r);
                handleSms(ctx.cur, ctx.suggestedName, progress, seenKeys, batch);
                ctx.cur = null; ctx.suggestedName = null;
            }
            case "mms", "rcs" -> {
                ctx.suggestedName = nullIfBlank(attr(r, "contact_name"));
                ctx.cur = buildMultipartHeaderStreaming(r, local.equals("mms") ? MessageProtocol.MMS : MessageProtocol.RCS);
                startMultipart();
                ctx.curParts = new ArrayList<>();
                ctx.curMedia = new ArrayList<>();
                ctx.textAgg = new StringBuilder();
                ctx.inMultipart = true;
            }
            case "part" -> {
                if (ctx.inMultipart && ctx.cur != null) {
                    handlePart(r, ctx.cur, ctx.curParts, ctx.curMedia, ctx.textAgg);
                }
            }
            case "addr" -> {
                if (ctx.inMultipart && ctx.cur != null) {
                    accumulateAddressStreaming(r, ctx.cur);
                }
            }
            default -> { /* no-op */ }
        }
        return ctx;
    }

    private ElementContext handleEndElement(XMLStreamReader r,
                                            ElementContext ctx,
                                            ImportProgress progress,
                                            Set<String> seenKeys,
                                            List<Message> batch) {
        String local = r.getLocalName();
        if (ctx.inMultipart && ctx.cur != null && (local.equals("mms") || local.equals("rcs"))) {
            finalizeMultipart(ctx.cur, ctx.suggestedName, ctx.curParts, ctx.curMedia, ctx.textAgg);
            boolean dup = isDuplicateInRunOrDb(ctx.cur, seenKeys);
            if (dup) { progress.incDuplicateMessages(); }
            else { batch.add(ctx.cur); progress.incImportedMessages(); }
            progress.incProcessedMessages();
            flushStreamingIfNeeded(batch, progress);
            ctx.cur = null; ctx.curParts = null; ctx.curMedia = null; ctx.textAgg = null; ctx.inMultipart = false; ctx.suggestedName = null;
        }
        return ctx;
    }

    // Re-added after refactor: placeholder hook for future metrics or state init
    private void startMultipart() { /* placeholder for future metrics */ }

    private void handlePart(XMLStreamReader r,
                            Message cur,
                            List<MessagePart> curParts,
                            List<Map<String,Object>> curMedia,
                            StringBuilder textAgg) {

        MessagePart part = buildPartStreaming(r, cur, curParts.size());
        curParts.add(part);

        String ct = Optional.ofNullable(part.getContentType()).orElse("");

        // Aggregate text parts
        if (part.getText() != null && ct.startsWith(TEXT_PLAIN)) {
            textAgg.append(part.getText()).append(' ');
        }

        // Non-text & non-SMIL parts are treated as media
        if (!ct.equalsIgnoreCase(TEXT_PLAIN) && !ct.equalsIgnoreCase(APPLICATION_SMIL)) {
            Map<String,Object> mediaMap = new LinkedHashMap<>();
            mediaMap.put("seq", part.getSeq());
            mediaMap.put("contentType", ct);
            mediaMap.put("name", Optional.ofNullable(part.getName()).orElse(""));

            String fp = part.getFilePath();
            if (fp == null) {
                // Base64 invalid or absent; skip thumbnail work
                log.warn("Media part seq={} skipped (no filePath, probably invalid/missing Base64)", part.getSeq());
                mediaMap.put("filePath", null);
                curMedia.add(mediaMap);
                return;
            }

            // Use stored path directly (already rooted); avoid duplicating media root
            Path mediaPath = Paths.get(fp).normalize();
            mediaMap.put("filePath", mediaPath.toString());
            curMedia.add(mediaMap);

            // If file exists we can optionally (re)generate a thumbnail if missing
            if (Files.exists(mediaPath)) {
                try {
                    Path thumbPath = thumbnailService.deriveThumbnailPath(mediaPath, part.getSeq());
                    if (!Files.exists(thumbPath)) {
                        thumbnailService.createThumbnail(mediaPath, thumbPath, ct, true);
                    }
                } catch (Exception ex) {
                    log.warn("Thumbnail generation failed for {}", mediaPath, ex);
                }
            } else {
                log.warn("Media file not found on disk seq={} path={}", part.getSeq(), mediaPath);
            }
        }
    }

    private void finalizeMultipart(Message cur, String suggestedName, List<MessagePart> curParts, List<Map<String,Object>> curMedia, StringBuilder textAgg) {
        if (StringUtils.isBlank(cur.getBody()) && textAgg != null && !textAgg.isEmpty()) {
            cur.setBody(textAgg.toString().trim());
        }
        if (curMedia != null && !curMedia.isEmpty()) cur.setMedia(Map.of("parts", curMedia));
        cur.setParts(curParts);
        finalizeStreamingContact(cur, suggestedName);
        // After contact is resolved relocate any parts saved under _nocontact
        relocatePartsToContactDir(cur);
    }

    private boolean isDuplicateInRunOrDb(Message msg, Set<String> seenKeys) {
        String key = buildDuplicateKey(msg);
        if (seenKeys.contains(key)) return true;
        boolean dbDup = isDuplicate(msg);
        if (!dbDup) seenKeys.add(key);
        return dbDup;
    }
    private String buildDuplicateKey(Message msg) {
        String bodyNorm = msg.getBody() == null ? "" : msg.getBody().trim();
        Long contactId = msg.getContact() == null ? null : msg.getContact().getId();
        String contactStr = (contactId == null) ? "null" : contactId.toString();
        return contactStr + "|" + msg.getTimestamp() + "|" + msg.getMsgBox() + "|" + msg.getProtocol() + "|" + bodyNorm;
    }

    private void flushStreamingIfNeeded(List<Message> batch, ImportProgress progress) { if (batch.size() >= streamBatchSize) flushStreamingBatch(batch, progress); }
    private void flushStreamingBatch(List<Message> batch, ImportProgress progress) { if (batch.isEmpty()) return; try { messageRepo.saveAll(batch); batch.clear(); } catch (Exception e) { log.error("Batch persist failed size={}", batch.size(), e); progress.setStatus("FAILED"); progress.setError("Persistence error: " + e.getMessage()); } }
    private Message buildSmsStreaming(XMLStreamReader r) { Message msg = new Message(); msg.setProtocol(MessageProtocol.SMS); msg.setTimestamp(parseInstant(r.getAttributeValue(null, "date"))); msg.setBody(r.getAttributeValue(null, "body")); int box = parseInt(r.getAttributeValue(null, "type"), 0); msg.setMsgBox(box); msg.setDirection(box == MSG_BOX_INBOX ? MessageDirection.INBOUND : MessageDirection.OUTBOUND); String address = r.getAttributeValue(null, "address"); if (msg.getDirection() == MessageDirection.INBOUND) { msg.setSender(address); msg.setRecipient(SENDER_ME); } else { msg.setSender(SENDER_ME); msg.setRecipient(address); } return msg; }
    private Message buildMultipartHeaderStreaming(XMLStreamReader r, MessageProtocol protocol) { Message msg = new Message(); msg.setProtocol(protocol); msg.setTimestamp(parseInstant(r.getAttributeValue(null, "date"))); int box = parseInt(r.getAttributeValue(null, "msg_box"), 0); msg.setMsgBox(box); msg.setDirection(box == MSG_BOX_INBOX ? MessageDirection.INBOUND : MessageDirection.OUTBOUND); if (protocol == MessageProtocol.RCS) { String bodyAttr = nullIfBlank(r.getAttributeValue(null, "body")); if (bodyAttr != null) msg.setBody(bodyAttr); } return msg; }
    private void accumulateAddressStreaming(XMLStreamReader r, Message msg) { String type = r.getAttributeValue(null, "type"), address = r.getAttributeValue(null, "address"); if (type == null || address == null) return; if (ADDR_TYPE_FROM.equals(type)) msg.setSender(address); else if (ADDR_TYPE_TO.equals(type)) { if (msg.getRecipient() == null || msg.getRecipient().isBlank()) msg.setRecipient(address); else msg.setRecipient(msg.getRecipient()+","+address); } }
    private void finalizeStreamingContact(Message msg, String suggestedName) {
        if (msg.getMsgBox() != null) {
            if (msg.getMsgBox() == MSG_BOX_SENT) msg.setSender(SENDER_ME);
            else if (msg.getMsgBox() == MSG_BOX_INBOX) msg.setRecipient(SENDER_ME);
        }
        String counterparty = pickCounterparty(msg);
        // Prefer ThreadLocal user (async import) fallback to provider
        User user = threadLocalImportUser.get();
        if (user == null) {
            user = currentUserProvider.getCurrentUser();
        }
        msg.setUser(user);
        Contact contact = resolveContact(user, counterparty, suggestedName);
        msg.setContact(contact);
    }
    private MessagePart buildPartStreaming(XMLStreamReader r, Message msg, int idx) { MessagePart part = new MessagePart(); part.setMessage(msg); part.setSeq(parseInt(r.getAttributeValue(null, "seq"), idx)); part.setContentType(r.getAttributeValue(null, "ct")); part.setName(nullIfBlank(r.getAttributeValue(null, "name"))); part.setText(nullIfBlank(r.getAttributeValue(null, "text"))); String data = r.getAttributeValue(null, "data"); if (data != null && !data.isBlank()) { saveMediaPart(data, part).ifPresent(part::setFilePath); if (part.getFilePath() != null) { try { part.setSizeBytes(Files.size(Path.of(part.getFilePath()))); } catch (Exception ignored) {} } } return part; }

    private Optional<String> saveMediaPart(String base64, MessagePart part) {
        try {
            // Validate Base64 input
            if (!isValidBase64(base64)) {
                log.error("Invalid Base64 input: {}", base64);
                return Optional.empty();
            }

            Message message = part.getMessage();
            // Determine user (threadLocal first, then message, then provider) for directory scoping
            User user = threadLocalImportUser.get();
            if (user == null) user = message.getUser();
            if (user == null) {
                try { currentUserProvider.getCurrentUser(); } catch (Exception ignored) {}
            }
            // Contact may not yet be resolved (during part parsing). Use contactId if present else temporary _nocontact folder.
            String contactDirName = (message.getContact() != null && message.getContact().getId() != null)
                    ? message.getContact().getId().toString()
                    : "_nocontact";
            Path baseRoot = getMediaRoot();
            Path dir = baseRoot.resolve(contactDirName);
            Files.createDirectories(dir);
            String ext = guessExtension(part.getContentType(), part.getName());
            Path original = dir.resolve("part" + part.getSeq() + ext);
            // Avoid accidental overwrite if same seq reused across messages before relocation (unlikely). If exists, append timestamp.
            if (Files.exists(original)) {
                original = dir.resolve("part" + part.getSeq() + "_" + System.currentTimeMillis() + ext);
            }
            Files.write(original, Base64.getDecoder().decode(base64));

            // Delegate thumbnail creation to ThumbnailService
            Path thumbPath = thumbnailService.deriveThumbnailPath(original, part.getSeq());
            thumbnailService.createThumbnail(original, thumbPath, part.getContentType(), false);

            part.setFilePath(original.toString());
            try { part.setSizeBytes(Files.size(original)); } catch (Exception ignored) {}
            return Optional.of(original.toString());
        } catch (Exception e) { log.error("Media save failed", e); return Optional.empty(); }
    }

    private boolean isValidBase64(String base64) {
        try {
            Base64.getDecoder().decode(base64);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static class ImportProgress {
        private final UUID id;
        private final long totalBytes;
        private final AtomicLong bytesRead = new AtomicLong(0);
        private final AtomicLong processedMessages = new AtomicLong(0);
        private final AtomicLong importedMessages = new AtomicLong(0);
        private final AtomicLong duplicateMessagesAtomic = new AtomicLong(0);
        private volatile int duplicateMessages;
        private volatile String status = "PENDING";
        private volatile String error;
        private volatile Instant startedAt;
        private volatile Instant finishedAt;

        public ImportProgress(UUID id, long totalBytes) { this.id=id; this.totalBytes=totalBytes; }

        public long getBytesRead(){return bytesRead.get();}
        public long getProcessedMessages(){return processedMessages.get();}
        public long getImportedMessages(){return importedMessages.get();}
        public long getDuplicateMessages(){return duplicateMessagesAtomic.get();}
        public double getPercentBytes(){return totalBytes==0?0.0:Math.min(100.0,(getBytesRead()*100.0)/totalBytes);}
        public UUID getId(){return id;}
        public long getTotalBytes(){return totalBytes;}
        public int getDuplicateMessagesFinal(){return duplicateMessages;}
        public String getStatus(){return status;}
        public String getError(){return error;}
        public Instant getStartedAt(){return startedAt;}
        public Instant getFinishedAt(){return finishedAt;}

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
    // ===== End Streaming Import =====

    private boolean isDuplicate(Message msg) {
        try {
            String normalizedBody = msg.getBody() == null ? null : msg.getBody().trim();
            if (postgresDialect && msg.getContact() != null && msg.getContact().getId() != null) {
                return messageRepo.existsDuplicateHash(
                        msg.getContact().getId(),
                        msg.getTimestamp(),
                        msg.getMsgBox(),
                        msg.getProtocol(),
                        normalizedBody
                );
            }
            return messageRepo.existsDuplicate(
                    msg.getContact(),
                    msg.getTimestamp(),
                    msg.getMsgBox(),
                    msg.getProtocol(),
                    normalizedBody
            );
        } catch (Exception e) {
            log.warn("Duplicate check failed, proceeding to insert message: {}", e.getMessage());
            return false;
        }
    }

    private String pickCounterparty(Message msg) {
        if (msg.getDirection() == MessageDirection.INBOUND) {
            return msg.getSender();
        }
        if (msg.getRecipient() == null) return null;
        return Arrays.stream(msg.getRecipient().split(","))
                .map(String::trim)
                .filter(s -> !s.equalsIgnoreCase(SENDER_ME) && !s.isBlank())
                .findFirst()
                .orElse(null);
    }

    private Contact resolveContact(com.joshfouchey.smsarchive.model.User user, String number, String suggestedName) {
        String normalized = normalizeNumber(number);
        String cacheKey = user.getId()+"|"+normalized;
        Contact cached = contactCache.get(cacheKey);
        if (cached != null) return cached;
        Contact contact = contactRepo.findByUserAndNormalizedNumber(user, normalized).orElseGet(() -> {
            Contact c = new Contact();
            c.setUser(user);
            c.setNumber(number == null ? UNKNOWN_NUMBER_DISPLAY : number);
            c.setNormalizedNumber(normalized);
            c.setName(suggestedName);
            return contactRepo.save(c);
        });
        contactCache.put(cacheKey, contact);
        return contact;
    }

    @VisibleForTesting
    String normalizeNumber(String number) { if (number == null || number.isBlank()) return UNKNOWN_NORMALIZED; return number.replaceAll("\\D", ""); }

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

    private void relocatePartsToContactDir(Message msg) {
        if (msg.getContact() == null || msg.getContact().getId() == null || msg.getParts() == null) return;
        Path baseRoot = getMediaRoot();
        Path targetDir = baseRoot.resolve(msg.getContact().getId().toString());
        try { Files.createDirectories(targetDir); } catch (Exception e) { log.error("Failed creating target media dir {}", targetDir, e); return; }
        for (MessagePart part : msg.getParts()) {
            String fp = part.getFilePath();
            if (fp == null) continue;
            Path current = Paths.get(fp).normalize();
            Path parent = current.getParent();
            if (parent == null) continue;
            if (!"_nocontact".equals(parent.getFileName().toString())) continue; // only relocate temp
            try {
                Path newPath = targetDir.resolve(current.getFileName());
                if (Files.exists(newPath)) {
                    // Resolve collision by appending UUID
                    String baseName = current.getFileName().toString();
                    int dotIdx = baseName.lastIndexOf('.');
                    String namePart = dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName;
                    String ext = dotIdx > 0 ? baseName.substring(dotIdx) : "";
                    newPath = targetDir.resolve(namePart + "_" + UUID.randomUUID() + ext);
                }
                Files.move(current, newPath, StandardCopyOption.REPLACE_EXISTING);
                part.setFilePath(newPath.toString());
                // Move thumb if exists (supports previous naming part{seq}_thumb.jpg)
                String thumbCandidate = "part" + part.getSeq() + "_thumb.jpg";
                Path oldThumb = parent.resolve(thumbCandidate);
                if (Files.exists(oldThumb)) {
                    Path newThumb = targetDir.resolve(oldThumb.getFileName());
                    if (Files.exists(newThumb)) {
                        newThumb = targetDir.resolve("part" + part.getSeq() + "_thumb_" + UUID.randomUUID() + ".jpg");
                    }
                    try { Files.move(oldThumb, newThumb, StandardCopyOption.REPLACE_EXISTING); } catch (Exception ex) { log.warn("Failed moving thumbnail {}", oldThumb, ex); }
                }
            } catch (Exception ex) {
                log.warn("Failed to relocate media part {}", current, ex);
            }
        }
    }
}
