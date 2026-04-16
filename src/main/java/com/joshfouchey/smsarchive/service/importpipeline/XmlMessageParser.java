package com.joshfouchey.smsarchive.service.importpipeline;

import com.joshfouchey.smsarchive.model.*;
import com.joshfouchey.smsarchive.service.MediaRelocationHelper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import javax.xml.stream.XMLStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Supplier;

@Slf4j
public class XmlMessageParser {

    private static final String SENDER_ME = "me";
    private static final String ADDR_TYPE_FROM = "137";
    private static final String ADDR_TYPE_TO = "151";
    private static final int MSG_BOX_INBOX = 1;
    private static final String TEXT_PLAIN = "text/plain";
    private static final String APPLICATION_SMIL = "application/smil";
    private static final String UNKNOWN_NORMALIZED = "__unknown__";
    private static final String XML_ATTR_ADDRESS = "address";
    private static final String META_TEMP_SENDER = "_tempSender";
    private static final String META_TEMP_RECIPIENT = "_tempRecipient";
    private static final String META_TEMP_ADDRESS = "_tempAddress";
    private static final String META_NORMALIZED_NUMBER = "_normalizedNumber";

    private final ContactResolver contactResolver;
    private final MediaHandler mediaHandler;
    private final MediaRelocationHelper mediaRelocationHelper;
    private final Supplier<User> userSupplier;

    public XmlMessageParser(ContactResolver contactResolver,
                            MediaHandler mediaHandler,
                            MediaRelocationHelper mediaRelocationHelper,
                            Supplier<User> userSupplier) {
        this.contactResolver = contactResolver;
        this.mediaHandler = mediaHandler;
        this.mediaRelocationHelper = mediaRelocationHelper;
        this.userSupplier = userSupplier;
    }

    // ===== Callbacks for orchestration =====

    @FunctionalInterface
    public interface SmsReadyHandler {
        void handle(Message msg, String suggestedName);
    }

    @FunctionalInterface
    public interface MultipartReadyHandler {
        void handle(Message msg, String threadKey, Set<String> participantNumbers, String suggestedName);
    }

    // ===== Element context (mutable parsing state) =====

    @Getter
    public static class ElementContext {
        Message cur;
        List<MessagePart> curParts;
        List<Map<String, Object>> curMedia;
        StringBuilder textAgg;
        String suggestedName;
        boolean inMultipart;
        Set<String> participantNumbers;
        String threadKey;

        public ElementContext() {}

        public ElementContext(Message c, List<MessagePart> parts, List<Map<String, Object>> media,
                              StringBuilder agg, String name, boolean multi,
                              Set<String> participants, String thread) {
            this.cur = c;
            this.curParts = parts;
            this.curMedia = media;
            this.textAgg = agg;
            this.suggestedName = name;
            this.inMultipart = multi;
            this.participantNumbers = participants;
            this.threadKey = thread;
        }

        void reset() {
            cur = null;
            curParts = null;
            curMedia = null;
            textAgg = null;
            inMultipart = false;
            suggestedName = null;
            participantNumbers = null;
            threadKey = null;
        }
    }

    // ===== Top-level XML dispatch =====

    public void handleStartElement(XMLStreamReader r, ElementContext ctx, SmsReadyHandler onSms) {
        String local = r.getLocalName();
        switch (local) {
            case "sms" -> handleStartSms(r, ctx, onSms);
            case "mms", "rcs" -> startMultipartMessage(r, ctx, local);
            case "part" -> handleMultipartPart(r, ctx);
            case "addr" -> processAddrElement(r, ctx);
            default -> { /* no-op */ }
        }
    }

    public void handleEndElement(XMLStreamReader r, ElementContext ctx, MultipartReadyHandler onMultipart) {
        String local = r.getLocalName();
        if (ctx.inMultipart && ctx.cur != null && ("mms".equals(local) || "rcs".equals(local))) {
            finalizeMultipart(ctx.cur, ctx.suggestedName, ctx.curParts, ctx.curMedia,
                    ctx.textAgg, ctx.participantNumbers);
            onMultipart.handle(ctx.cur, ctx.threadKey, ctx.participantNumbers, ctx.suggestedName);
            ctx.reset();
        }
    }

    // ===== SMS handling =====

    private void handleStartSms(XMLStreamReader r, ElementContext ctx, SmsReadyHandler onSms) {
        ctx.suggestedName = nullIfBlank(attr(r, "contact_name"));
        ctx.cur = buildSmsStreaming(r);
        finalizeStreamingContact(ctx.cur, ctx.suggestedName);
        onSms.handle(ctx.cur, ctx.suggestedName);
        ctx.cur = null;
        ctx.suggestedName = null;
    }

    // ===== Multipart setup =====

    private void startMultipartMessage(XMLStreamReader r, ElementContext ctx, String local) {
        ctx.suggestedName = nullIfBlank(attr(r, "contact_name"));
        ctx.cur = buildMultipartHeaderStreaming(r, local.equals("mms") ? MessageProtocol.MMS : MessageProtocol.RCS);
        ctx.threadKey = nullIfBlank(attr(r, XML_ATTR_ADDRESS));
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

    // ===== Address element processing =====

    private void processAddrElement(XMLStreamReader r, ElementContext ctx) {
        if (!(ctx.inMultipart && ctx.cur != null)) return;
        accumulateAddressStreaming(r, ctx.cur);
        String addrVal = attr(r, XML_ATTR_ADDRESS);
        String addrType = attr(r, "type");
        if (!shouldConsiderAddrForParticipants(ctx.cur, addrType)) return;
        if (addrVal == null || addrVal.isBlank() || addrVal.equalsIgnoreCase(SENDER_ME)) return;
        if (ctx.participantNumbers == null) ctx.participantNumbers = new LinkedHashSet<>();
        String norm = contactResolver.normalizeNumber(addrVal);
        if (!UNKNOWN_NORMALIZED.equals(norm)) ctx.participantNumbers.add(norm);
    }

    private boolean shouldConsiderAddrForParticipants(Message cur, String addrType) {
        if (cur.getDirection() == MessageDirection.INBOUND) {
            return ADDR_TYPE_FROM.equals(addrType) || "130".equals(addrType);
        }
        if (cur.getDirection() == MessageDirection.OUTBOUND) {
            return ADDR_TYPE_TO.equals(addrType);
        }
        return false;
    }

    // ===== Contact finalization =====

    void finalizeStreamingContact(Message msg, String suggestedName) {
        Map<String, Object> meta = msg.getMetadata();
        String tempSender = meta != null ? (String) meta.get(META_TEMP_SENDER) : null;
        String tempRecipient = meta != null ? (String) meta.get(META_TEMP_RECIPIENT) : null;
        String tempAddress = meta != null ? (String) meta.get(META_TEMP_ADDRESS) : null;
        log.debug("finalizeStreamingContact - protocol: {}, direction: {}",
                msg.getProtocol(), msg.getDirection());
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
        User user = userSupplier.get();
        msg.setUser(user);
        log.debug("finalizeStreamingContact - resolved counterparty");
        Contact contact = contactResolver.resolveContact(user, counterparty, suggestedName);
        log.debug("finalizeStreamingContact - resolved contact: id={}",
                contact != null ? contact.getId() : null);
        if (msg.getDirection() == MessageDirection.INBOUND && senderAddress != null) {
            msg.setSenderContact(contact);
        } else {
            msg.setSenderContact(null);
        }
        if (contact != null && meta != null) {
            meta.put(META_NORMALIZED_NUMBER, contact.getNormalizedNumber());
        }
        cleanupTempMetadata(meta);
    }

    void finalizeGroupMessageContact(Message msg) {
        Map<String, Object> meta = msg.getMetadata();
        String tempSender = meta != null ? (String) meta.get(META_TEMP_SENDER) : null;
        String tempAddress = meta != null ? (String) meta.get(META_TEMP_ADDRESS) : null;
        User user = userSupplier.get();
        msg.setUser(user);
        String senderAddress = (msg.getDirection() == MessageDirection.INBOUND)
                ? (tempSender != null ? tempSender : tempAddress) : null;
        log.debug("finalizeGroupMessageContact - direction: {}", msg.getDirection());
        if (senderAddress != null && !senderAddress.isBlank()) {
            Contact sender = contactResolver.resolveContact(user, senderAddress, null);
            msg.setSenderContact(sender);
            log.debug("finalizeGroupMessageContact - resolved sender: id={}",
                    sender != null ? sender.getId() : null);
        } else {
            msg.setSenderContact(null);
        }
        cleanupTempMetadata(meta);
    }

    // ===== Multipart finalization =====

    private void finalizeMultipart(Message cur, String suggestedName,
                                   List<MessagePart> curParts, List<Map<String, Object>> curMedia,
                                   StringBuilder textAgg, Set<String> participantNumbers) {
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
        mediaRelocationHelper.relocate(cur);
    }

    // ===== Message building =====

    Message buildSmsStreaming(XMLStreamReader r) {
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

    Message buildMultipartHeaderStreaming(XMLStreamReader r, MessageProtocol protocol) {
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

    // ===== Part handling =====

    private void handlePart(XMLStreamReader r, Message cur, List<MessagePart> curParts,
                            List<Map<String, Object>> curMedia, StringBuilder textAgg) {
        MessagePart part = buildPartStreaming(r, cur, curParts.size());
        curParts.add(part);
        String ct = Optional.ofNullable(part.getContentType()).orElse("");
        if (part.getText() != null && ct.startsWith(TEXT_PLAIN)) textAgg.append(part.getText()).append(' ');
        if (!ct.equalsIgnoreCase(TEXT_PLAIN) && !ct.equalsIgnoreCase(APPLICATION_SMIL)) {
            Map<String, Object> mediaMap = new LinkedHashMap<>();
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
                mediaHandler.ensureThumbnail(mediaPath, ct, true);
            }
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
            mediaHandler.saveMediaPart(data, part).ifPresent(part::setFilePath);
            if (part.getFilePath() != null) {
                mediaHandler.safeSetSize(part, Path.of(part.getFilePath()));
            }
        }
        return part;
    }

    // ===== Address/metadata helpers =====

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

    private Map<String, Object> ensureMetadata(Message msg) {
        Map<String, Object> meta = msg.getMetadata();
        if (meta == null) { meta = new HashMap<>(); msg.setMetadata(meta); }
        return meta;
    }

    private void appendRecipient(Map<String, Object> meta, String address) {
        String existing = (String) meta.get(META_TEMP_RECIPIENT);
        if (existing == null || existing.isBlank()) meta.put(META_TEMP_RECIPIENT, address);
        else meta.put(META_TEMP_RECIPIENT, existing + "," + address);
    }

    private void cleanupTempMetadata(Map<String, Object> meta) {
        if (meta == null) return;
        meta.remove(META_TEMP_SENDER);
        meta.remove(META_TEMP_RECIPIENT);
        meta.remove(META_TEMP_ADDRESS);
        if (meta.isEmpty()) { /* leave empty map to be cleaned later */ }
    }

    private String pickFirstNonMe(String recipients) {
        if (recipients == null) return null;
        return Arrays.stream(recipients.split(","))
                .map(String::trim)
                .filter(s -> !s.equalsIgnoreCase(SENDER_ME) && !s.isBlank())
                .findFirst()
                .orElse(recipients);
    }

    // ===== Utility helpers =====

    public Instant parseInstant(String millisStr) {
        if (StringUtils.isBlank(millisStr)) return Instant.EPOCH;
        String trimmed = millisStr.trim();
        if (NumberUtils.isDigits(trimmed)) {
            try {
                long ms = Long.parseLong(trimmed);
                if (trimmed.length() <= 10) return Instant.ofEpochSecond(ms);
                return Instant.ofEpochMilli(ms);
            } catch (NumberFormatException _) { /* fall through to ISO parse */ }
        }
        try { return Instant.parse(trimmed); }
        catch (DateTimeParseException ex) { log.warn("Unparseable timestamp '{}'", millisStr); return Instant.EPOCH; }
    }

    int parseInt(String val, int def) {
        return NumberUtils.toInt(StringUtils.trimToEmpty(val), def);
    }

    String nullIfBlank(String s) { return StringUtils.isBlank(s) ? null : s; }

    String attr(XMLStreamReader r, String name) { return r.getAttributeValue(null, name); }
}
