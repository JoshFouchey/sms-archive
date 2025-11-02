package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.*;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.ConversationRepository;
import com.joshfouchey.smsarchive.repository.ConversationMessageRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Slf4j
@Service
public class ImportService {

    private boolean postgresDialect; // set via DataSource detection

    private static final String SENDER_ME = "me";
    private static final String ADDR_TYPE_FROM = "137";
    private static final String ADDR_TYPE_TO = "151";
    private static final String ADDR_TYPE_PARTICIPANT = "130"; // newly handled group participant type
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
    private final ConversationRepository conversationRepo;
    private final ConversationMessageRepository conversationMessageRepo;
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

    private TransactionTemplate txTemplate; // initialized via setTxManager

    @PersistenceContext
    private EntityManager entityManager;

    Path getMediaRoot() {
        return Paths.get(mediaRoot);
    }
    @PostConstruct
    private void logMediaRootAtStartup() {
        // Absolute path helps confirm volume mounts
        log.info("Resolved media root: {}", Paths.get(mediaRoot).toAbsolutePath());
    }

    public ImportService(MessageRepository messageRepo, ContactRepository contactRepo,
                         CurrentUserProvider currentUserProvider, ThumbnailService thumbnailService,
                         ConversationRepository conversationRepository,
                         ConversationMessageRepository conversationMessageRepository) {
        this.messageRepo = messageRepo;
        this.contactRepo = contactRepo;
        this.currentUserProvider = currentUserProvider;
        this.thumbnailService = thumbnailService;
        this.conversationRepo = conversationRepository;
        this.conversationMessageRepo = conversationMessageRepository;
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

    @Autowired
    public void setTxManager(PlatformTransactionManager txManager) {
        this.txTemplate = new TransactionTemplate(txManager);
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
            try {
                if (txTemplate != null) {
                    txTemplate.executeWithoutResult(status -> runStreamingImportAsync(jobId, xmlPath));
                } else {
                    runStreamingImportAsync(jobId, xmlPath);
                }
            } catch (Exception ex) {
                log.error("Import failed job={} path={}", jobId, xmlPath, ex);
                ImportProgress p = progressMap.get(jobId);
                if (p != null) {
                    p.setStatus("FAILED");
                    p.setError(ex.getMessage());
                    p.setFinishedAt(Instant.now());
                }
            } finally {
                threadLocalImportUser.remove();
            }
        };
        if (importInline) { task.run(); return jobId; }
        if (importTaskExecutor != null) importTaskExecutor.execute(task); else { Thread t = new Thread(task, "import-worker-fallback-"+jobId); t.setDaemon(true); t.start(); }
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

        if (part.getText() != null && ct.startsWith(TEXT_PLAIN)) {
            textAgg.append(part.getText()).append(' ');
        }

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
                return;
            }

            Path mediaPath = Paths.get(fp).normalize();
            mediaMap.put("filePath", mediaPath.toString());
            curMedia.add(mediaMap);

            if (Files.exists(mediaPath)) {
                try {
                    Path thumbPath = thumbnailService.deriveStemThumbnail(mediaPath);
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

    private MessagePart buildPartStreaming(XMLStreamReader r, Message msg, int index) {
        MessagePart part = new MessagePart();
        part.setMessage(msg);
        String seqAttr = attr(r, "seq");
        int seq = NumberUtils.toInt(StringUtils.trimToEmpty(seqAttr), index);
        part.setSeq(seq);
        String ct = attr(r, "ct");
        part.setContentType(ct);
        String name = nullIfBlank(attr(r, "name"));
        if (name == null) name = nullIfBlank(attr(r, "cl"));
        part.setName(name);
        String text = nullIfBlank(attr(r, "text"));
        part.setText(text);
        String data = attr(r, "data");
        if (StringUtils.isNotBlank(data) && StringUtils.isNotBlank(ct) && !ct.equalsIgnoreCase(TEXT_PLAIN) && !ct.equalsIgnoreCase(APPLICATION_SMIL)) {
            try {
                byte[] bytes = Base64.getDecoder().decode(data);
                Path baseRoot = getMediaRoot();
                Path nocontactDir = baseRoot.resolve("_nocontact");
                Files.createDirectories(nocontactDir);
                String ext = guessExtension(ct, name);
                String fileBase = System.currentTimeMillis()+"_"+UUID.randomUUID();
                Path out = nocontactDir.resolve(fileBase + ext);
                Files.write(out, bytes);
                part.setFilePath(out.toString());
                part.setSizeBytes((long) bytes.length);
            } catch (Exception ex) {
                log.warn("Failed decoding/storing media part seq={} ct={} err={}", seq, ct, ex.getMessage());
            }
        }
        return part;
    }

    private void finalizeMultipart(Message cur, String suggestedName, List<MessagePart> curParts, List<Map<String,Object>> curMedia, StringBuilder textAgg) {
        if (StringUtils.isBlank(cur.getBody()) && textAgg != null && !curMedia.isEmpty()) {
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
        Long conversationId = msg.getConversation() == null ? null : msg.getConversation().getId();
        String convoStr = (conversationId == null) ? "null" : conversationId.toString();
        return convoStr + "|" + msg.getTimestamp() + "|" + msg.getMsgBox() + "|" + msg.getProtocol() + "|" + bodyNorm;
    }

    private void flushStreamingIfNeeded(List<Message> batch, ImportProgress progress) { if (batch.size() >= streamBatchSize) flushStreamingBatch(batch, progress); }
    private void flushStreamingBatch(List<Message> batch, ImportProgress progress) { if (batch.isEmpty()) return; try { messageRepo.saveAll(batch); batch.clear(); } catch (Exception e) { log.error("Batch persist failed size={}", batch.size(), e); progress.setStatus("FAILED"); progress.setError("Persistence error: " + e.getMessage()); } }
    private Message buildSmsStreaming(XMLStreamReader r) {
        Message msg = new Message();
        msg.setProtocol(MessageProtocol.SMS);
        msg.setTimestamp(parseInstant(r.getAttributeValue(null, "date")));
        msg.setBody(r.getAttributeValue(null, "body"));
        int box = parseInt(r.getAttributeValue(null, "type"), 0);
        msg.setMsgBox(box);
        msg.setDirection(box == MSG_BOX_INBOX ? MessageDirection.INBOUND : MessageDirection.OUTBOUND);
        String address = r.getAttributeValue(null, "address");
        if (msg.getDirection() == MessageDirection.INBOUND) {
            msg.setSender(address);
            msg.setRecipient(SENDER_ME);
        } else {
            msg.setSender(SENDER_ME);
            msg.setRecipient(address);
        }
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
        // Capture raw conversation/thread address (RCS group id or MMS thread identifier) if present
        String rawAddr = nullIfBlank(r.getAttributeValue(null, "address"));
        if (rawAddr != null) {
            // Stash in metadata to avoid entity change; will be pulled when resolving conversation
            Map<String,Object> meta = new LinkedHashMap<>();
            meta.put("rawThreadAddress", rawAddr);
            msg.setMetadata(meta);
        }
        return msg;
    }
    private void accumulateAddressStreaming(XMLStreamReader r, Message msg) {
        String type = r.getAttributeValue(null, "type");
        String address = r.getAttributeValue(null, "address");
        if (type == null || address == null) return;
        if (ADDR_TYPE_FROM.equals(type)) {
            msg.setSender(address);
        } else if (ADDR_TYPE_TO.equals(type) || ADDR_TYPE_PARTICIPANT.equals(type)) {
            if (msg.getRecipient() == null || msg.getRecipient().isBlank()) {
                msg.setRecipient(address);
            } else {
                msg.setRecipient(msg.getRecipient() + "," + address);
            }
        }
    }
    private void finalizeStreamingContact(Message msg, String suggestedName) {
        // Capture participants BEFORE normalizing direction so we don't collapse group recipients
        String rawSender = msg.getSender();
        String rawRecipient = msg.getRecipient();
        Set<String> others = new LinkedHashSet<>();
        if (rawSender != null && !rawSender.equalsIgnoreCase(SENDER_ME)) others.add(rawSender);
        if (rawRecipient != null) {
            Arrays.stream(rawRecipient.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank() && !s.equalsIgnoreCase(SENDER_ME))
                    .forEach(others::add);
        }
        boolean isGroup = others.size() > 1;
        // Direction rewrite without destroying group participant list
        if (msg.getMsgBox() != null) {
            if (msg.getMsgBox() == MSG_BOX_SENT) {
                msg.setSender(SENDER_ME);
            } else if (msg.getMsgBox() == MSG_BOX_INBOX && !isGroup) {
                // Only collapse single inbound recipient to 'me'
                msg.setRecipient(SENDER_ME);
            }
        }
        User user = threadLocalImportUser.get();
        if (user == null) user = currentUserProvider.getCurrentUser();
        msg.setUser(user);
        List<Contact> resolvedContacts = new ArrayList<>();
        for (String num : others) {
            resolvedContacts.add(resolveContact(user, num, suggestedName));
        }
        if (!isGroup && !resolvedContacts.isEmpty()) {
            msg.setContact(resolvedContacts.get(0));
        } else {
            msg.setContact(null);
        }
        String rawThreadAddress = null;
        if (msg.getMetadata() != null && msg.getMetadata().get("rawThreadAddress") instanceof String rt) rawThreadAddress = rt;
        Conversation convo = resolveConversation(user, msg.getProtocol(), resolvedContacts, !isGroup, rawThreadAddress);
        msg.setConversation(convo);
    }
    private Contact resolveContact(User user, String rawNumber, String suggestedName) {
        String rn = (rawNumber == null || rawNumber.isBlank()) ? UNKNOWN_NUMBER_DISPLAY : rawNumber;
        String normalized = normalizeNumber(rn);
        String key = user.getId() + "|" + normalized;
        Contact cached = contactCache.get(key);
        if (cached != null) {
            ensureContactName(cached); // retrofit rule for cached
            // Reattach if detached
            if (entityManager != null && cached.getId() != null && !entityManager.contains(cached)) {
                try {
                    cached = entityManager.getReference(Contact.class, cached.getId());
                    contactCache.put(key, cached);
                } catch (Exception e) {
                    log.warn("Failed reattaching cached contact id={} err={}", cached.getId(), e.getMessage());
                }
            }
            return cached;
        }
        final String finalNumber = rn;
        Contact contact = contactRepo.findByUserAndNormalizedNumber(user, normalized).orElseGet(() -> {
            Contact c = new Contact();
            c.setUser(user);
            c.setNumber(finalNumber);
            c.setNormalizedNumber(normalized);
            c.setName(suggestedName);
            ensureContactName(c); // apply before first save
            return contactRepo.save(c);
        });
        boolean changed = ensureContactName(contact); // upgrade legacy blank/(Unknown)
        if (changed) {
            try { contact = contactRepo.save(contact); } catch (Exception e) { log.warn("Failed updating contact name id={} err={}", contact.getId(), e.getMessage()); }
        }
        contactCache.put(key, contact);
        return contact;
    }
    private boolean ensureContactName(Contact c) {
        if (c == null) return false;
        String name = c.getName();
        if (name == null) {
            c.setName(c.getNumber());
            return true;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("unknown") || trimmed.equalsIgnoreCase("(unknown)")) {
            c.setName(c.getNumber());
            return true;
        }
        return false;
    }
    private Contact resolveSelfContact(User user) {
        final String normalized = "__self__";
        String key = user.getId() + "|" + normalized;
        Contact cached = contactCache.get(key);
        if (cached != null) {
            if (entityManager != null && cached.getId() != null && !entityManager.contains(cached)) {
                try {
                    cached = entityManager.getReference(Contact.class, cached.getId());
                    contactCache.put(key, cached);
                } catch (Exception e) {
                    log.warn("Failed reattaching self contact id={} err={}", cached.getId(), e.getMessage());
                }
            }
            return cached;
        }
        Contact contact = contactRepo.findByUserAndNormalizedNumber(user, normalized).orElseGet(() -> {
            Contact c = new Contact();
            c.setUser(user);
            c.setNumber(SENDER_ME);
            c.setNormalizedNumber(normalized);
            c.setName("Me");
            return contactRepo.save(c);
        });
        contactCache.put(key, contact);
        return contact;
    }
    private Conversation resolveConversation(User user, MessageProtocol protocol, List<Contact> participants, boolean single, String rawThreadAddress) {
        String key;
        if (single && !participants.isEmpty()) {
            key = "SINGLE|" + protocol + "|" + participants.get(0).getNormalizedNumber();
        } else if (rawThreadAddress != null && !rawThreadAddress.isBlank() && !looksLikePlainPhone(rawThreadAddress)) {
            key = "GROUP|" + protocol + "|RAW|" + rawThreadAddress.trim();
        } else {
            List<String> nums = participants.stream().map(Contact::getNormalizedNumber).sorted().toList();
            key = "GROUP|" + protocol + "|" + String.join("|", nums);
        }
        if (txTemplate != null) {
            return txTemplate.execute(status -> internalResolveConversation(user, protocol, participants, single, rawThreadAddress, key));
        }
        return internalResolveConversation(user, protocol, participants, single, rawThreadAddress, key);
    }
    private Conversation internalResolveConversation(User user, MessageProtocol protocol, List<Contact> participants, boolean single, String rawThreadAddress, String key) {
        Conversation convo = conversationRepo.findWithParticipantsByUserAndExternalThreadId(user, key)
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setUser(user);
                    c.setType(single ? "SINGLE" : "GROUP");
                    c.setExternalThreadId(key);
                    if (single && !participants.isEmpty()) {
                        Contact pc = participants.get(0);
                        c.setDisplayName(pc.getName() != null ? pc.getName() : pc.getNumber());
                    } else {
                        String display;
                        if (rawThreadAddress != null && !rawThreadAddress.isBlank() && !looksLikePlainPhone(rawThreadAddress)) {
                            display = rawThreadAddress.length() > 24 ? rawThreadAddress.substring(0, 24) + "…" : rawThreadAddress;
                        } else {
                            display = participants.stream()
                                    .map(ct -> ct.getName() != null ? ct.getName() : ct.getNumber())
                                    .limit(3)
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse("Group(" + participants.size() + ")");
                            if (participants.size() > 3) display += ", +" + (participants.size() - 3);
                        }
                        c.setDisplayName(display);
                    }
                    Conversation saved = conversationRepo.save(c);
                    if (saved == null) saved = c;
                    for (Contact ct : participants) addParticipantIfAbsent(saved, ct, false);
                    if (!single) addParticipantIfAbsent(saved, resolveSelfContact(user), true);
                    if (saved.getParticipants() != null) saved.getParticipants().size();
                    return saved;
                });
        boolean updated = false;
        for (Contact ct : participants) updated |= addParticipantIfAbsent(convo, ct, false);
        if (!single) updated |= addParticipantIfAbsent(convo, resolveSelfContact(user), true);
        if (updated) {
            convo = conversationRepo.save(convo);
            if (convo.getParticipants() != null) convo.getParticipants().size();
        }
        return convo;
    }

    private boolean isDuplicate(Message msg) {
        try {
            String normalizedBody = msg.getBody() == null ? null : msg.getBody().trim();
            if (postgresDialect && msg.getConversation() != null && msg.getConversation().getId() != null) {
                return conversationMessageRepo.existsConversationDuplicate(
                        msg.getConversation().getId(),
                        msg.getTimestamp(),
                        msg.getMsgBox(),
                        msg.getProtocol(),
                        normalizedBody
                );
            }
            return conversationMessageRepo.existsConversationDuplicate(
                    msg.getConversation().getId(),
                    msg.getTimestamp(),
                    msg.getMsgBox(),
                    msg.getProtocol(),
                    normalizedBody
            );
        } catch (Exception e) {
            log.warn("Duplicate check failed (conversation), proceeding: {}", e.getMessage());
            return false;
        }
    }

    // Helper: determine if address string is just a plain phone number (digits / +digits)
    private boolean looksLikePlainPhone(String addr) {
        if (addr == null) return false;
        String trimmed = addr.trim();
        String digits = trimmed.startsWith("+") ? trimmed.substring(1) : trimmed;
        return digits.matches("[0-9]{5,}");
    }

    private boolean addParticipantIfAbsent(Conversation convo, Contact ct, boolean selfFlag) {
        if (convo.getParticipants() == null) convo.setParticipants(new ArrayList<>());
        Long targetId = ct.getId(); boolean exists = false;
        for (ConversationParticipant p : convo.getParticipants()) {
            if (p.getContact() != null && Objects.equals(p.getContact().getId(), targetId))
            { exists = true; break; } }
        if (exists) return false;
        if (entityManager != null && ct.getId() != null && !entityManager.contains(ct)) {
            try { ct = entityManager.getReference(Contact.class, ct.getId()); }
            catch (Exception e) { log.warn("Failed to reattach contact id={} err={}", ct.getId(), e.getMessage()); } }
        ConversationParticipant cp = new ConversationParticipant(); cp.setConversation(convo);
        cp.setContact(ct); cp.setSelf(selfFlag); ConversationParticipant.Id id = new ConversationParticipant.Id(); id.setConversationId(convo.getId());
        id.setContactId(ct.getId()); cp.setId(id); convo.getParticipants().add(cp); return true;
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
    @VisibleForTesting String computeDuplicateKeyForTest(Message msg) {
        // Backward compatibility: if conversation not set yet, fall back to contact id prefix
        if (msg.getConversation() == null && msg.getContact() != null && msg.getContact().getId() != null) {
            String bodyNorm = msg.getBody() == null ? "" : msg.getBody().trim();
            return msg.getContact().getId() + "|" + msg.getTimestamp() + "|" + msg.getMsgBox() + "|" + msg.getProtocol() + "|" + bodyNorm;
        }
        return buildDuplicateKey(msg);
    }

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
                    String baseName = current.getFileName().toString();
                    int dotIdx = baseName.lastIndexOf('.');
                    String namePart = dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName;
                    String ext = dotIdx > 0 ? baseName.substring(dotIdx) : "";
                    newPath = targetDir.resolve(namePart + "_" + UUID.randomUUID() + ext);
                }
                Files.move(current, newPath, StandardCopyOption.REPLACE_EXISTING);
                part.setFilePath(newPath.toString());
                try {
                    Path oldThumb = thumbnailService.deriveStemThumbnail(current);
                    if (Files.exists(oldThumb)) {
                        Path relocatedThumb = targetDir.resolve(oldThumb.getFileName());
                        if (Files.exists(relocatedThumb)) {
                            Path unique = targetDir.resolve(UUID.randomUUID() + "_" + oldThumb.getFileName().toString());
                            Files.move(oldThumb, unique, StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            Files.move(oldThumb, relocatedThumb, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } catch (Exception thumbEx) {
                    log.warn("Thumbnail relocation failed for {}: {}", current, thumbEx.getMessage());
                }
            } catch (Exception moveEx) {
                log.warn("Media relocation failed for {}: {}", current, moveEx.getMessage());
            }
        }
    }

    @Getter
    public static class ImportProgress {
        private final UUID jobId;
        private final long totalBytes;
        private final AtomicLong bytesRead = new AtomicLong();
        private final AtomicLong importedMessages = new AtomicLong();
        private final AtomicLong duplicateMessages = new AtomicLong();
        private final AtomicLong processedMessages = new AtomicLong();
        private volatile String status = "PENDING";
        private volatile String error;
        private Instant startedAt;
        private Instant finishedAt;
        public ImportProgress(UUID jobId, long totalBytes) { this.jobId = jobId; this.totalBytes = totalBytes; }
        public void setStatus(String status) { this.status = status; }
        public void setError(String error) { this.error = error; }
        public void setBytesRead(long val) { this.bytesRead.set(val); }
        public void incImportedMessages() { this.importedMessages.incrementAndGet(); }
        public void incDuplicateMessages() { this.duplicateMessages.incrementAndGet(); }
        public void incProcessedMessages() { this.processedMessages.incrementAndGet(); }
        public long getBytesRead() { return bytesRead.get(); }
        public long getImportedMessages() { return importedMessages.get(); }
        public long getDuplicateMessages() { return duplicateMessages.get(); }
        public long getProcessedMessages() { return processedMessages.get(); }
        public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
        public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
        public int getDuplicateMessagesFinal() { return (int) duplicateMessages.get(); }
    }

    // Conversation creation logic overview:
    // - For single <sms> messages: address attribute is the counterparty phone number. We build a SINGLE key
    //   as "SINGLE|<protocol>|<normalizedNumber>". Suggested contact_name becomes Contact.name.
    // - For multipart <mms>/<rcs>: we parse <addr> elements (types 137 sender, 151 recipients). All non-'me'
    //   numbers become participants. If only one counterparty => SINGLE as above. If multiple => GROUP.
    // - For GROUP MMS/RCS we prefer a stable raw thread identifier from the top-level 'address' attribute when it
    //   looks like an RCS group id (non plain phone). Key pattern: "GROUP|<protocol>|RAW|<rawThreadId>".
    // - Otherwise we deterministically sort normalized participant numbers and build key:
    //   "GROUP|<protocol>|<num1>|<num2>|..." to ensure new messages with same set map to same conversation.
    // - Contacts are distinct per (user, normalizedNumber) via unique index; each Contact can participate in many conversations.
    // - On every message import we ensure any newly seen participants are appended to existing conversation.
    // - Display name rules:
    //     SINGLE: contact.name or contact.number
    //     GROUP with raw thread id: truncated raw id (max 24 chars + ellipsis)
    //     GROUP without raw id: first up to 3 participant display names, then "+N" for extras.
    // - Duplicate detection uses conversation id + timestamp + msgBox + protocol + normalized body.
}
