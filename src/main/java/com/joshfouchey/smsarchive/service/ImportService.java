package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.*;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Service
public class ImportService {

    private static final Path MEDIA_ROOT = Paths.get("media/messages");

    private static final String SENDER_ME = "me";
    private static final String ADDR_TYPE_FROM = "137";
    private static final String ADDR_TYPE_TO = "151";
    private static final int MSG_BOX_INBOX = 1;
    private static final int MSG_BOX_SENT = 2;

    private final MessageRepository messageRepo;
    private final ContactRepository contactRepo;

    // cache normalizedNumber -> Contact
    private final Map<String, Contact> contactCache = new ConcurrentHashMap<>();

    public ImportService(MessageRepository messageRepo,
                         ContactRepository contactRepo) {
        this.messageRepo = messageRepo;
        this.contactRepo = contactRepo;
    }

    @CacheEvict(value = "analyticsDashboard", allEntries = true)
    public int importFromXml(File xmlFile) throws ParserConfigurationException, SAXException, java.io.IOException {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(xmlFile);
        doc.getDocumentElement().normalize();

        int duplicateCount = 0;
        List<Message> toPersist = new ArrayList<>();

        List<Message> sms = processSmsNodes(doc.getElementsByTagName("sms"));
        for (Message m : sms) {
            if (isDuplicate(m)) { duplicateCount++; continue; }
            toPersist.add(m);
        }
        List<Message> mms = processMultipartNodes(doc.getElementsByTagName("mms"), MessageProtocol.MMS);
        for (Message m : mms) {
            if (isDuplicate(m)) { duplicateCount++; continue; }
            toPersist.add(m);
        }
        List<Message> rcs = processMultipartNodes(doc.getElementsByTagName("rcs"), MessageProtocol.RCS);
        for (Message m : rcs) {
            if (isDuplicate(m)) { duplicateCount++; continue; }
            toPersist.add(m);
        }

        if (!toPersist.isEmpty()) {
            messageRepo.saveAll(toPersist);
        }
        int imported = toPersist.size();
        if (duplicateCount > 0) {
            log.info("Import skipped {} duplicate messages (imported: {})", duplicateCount, imported);
        } else {
            log.info("Import completed with {} messages imported (no duplicates)", imported);
        }
        return imported;
    }

    private boolean isDuplicate(Message msg) {
        try {
            String normalizedBody = msg.getBody() == null ? null : msg.getBody().trim();
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

    private List<Message> processSmsNodes(NodeList nodes) {
        return nodeListToStream(nodes)
                .map(el -> {
                    Message msg = new Message();
                    msg.setProtocol(MessageProtocol.SMS);
                    msg.setTimestamp(parseInstant(el.getAttribute("date")));
                    msg.setBody(el.getAttribute("body"));

                    int box = parseInt(el.getAttribute("type"), 0);
                    msg.setMsgBox(box);
                    msg.setDirection(box == MSG_BOX_INBOX ? MessageDirection.INBOUND : MessageDirection.OUTBOUND);

                    String address = el.getAttribute("address");
                    if (msg.getDirection() == MessageDirection.INBOUND) {
                        msg.setSender(address);
                        msg.setRecipient(SENDER_ME);
                    } else {
                        msg.setSender(SENDER_ME);
                        msg.setRecipient(address);
                    }

                    String rawName = nullIfBlank(el.getAttribute("contact_name"));
                    String counterparty = (msg.getDirection() == MessageDirection.INBOUND) ? msg.getSender() : msg.getRecipient();
                    Contact contact = resolveContact(counterparty, rawName);
                    msg.setContact(contact);

                    return msg;
                })
                .toList();
    }

    private List<Message> processMultipartNodes(NodeList nodes, MessageProtocol protocol) {
        return nodeListToStream(nodes)
                .map(el -> {
                    Message msg = new Message();
                    msg.setProtocol(protocol);
                    msg.setTimestamp(parseInstant(el.getAttribute("date")));
                    int box = parseInt(el.getAttribute("msg_box"), 0);
                    msg.setMsgBox(box);
                    msg.setDirection(box == MSG_BOX_INBOX ? MessageDirection.INBOUND : MessageDirection.OUTBOUND);

                    AddressInfo a = parseAddresses(el, box);
                    msg.setSender(a.sender());
                    msg.setRecipient(a.recipients());

                    processParts(el, msg);

                    if (protocol == MessageProtocol.RCS &&
                            (msg.getBody() == null || msg.getBody().isBlank())) {
                        msg.setBody(nullIfBlank(el.getAttribute("body")));
                    }

                    String rawName = nullIfBlank(el.getAttribute("contact_name"));
                    String counterparty = pickCounterparty(msg);
                    Contact contact = resolveContact(counterparty, rawName);
                    msg.setContact(contact);

                    return msg;
                })
                .toList();
    }

    private Contact resolveContact(String rawNumber, String suggestedName) {
        if (rawNumber == null || rawNumber.isBlank()) {
            return contactCache.computeIfAbsent("__unknown__", k ->
                    contactRepo.findByNormalizedNumber("__unknown__")
                            .orElseGet(() -> contactRepo.save(Contact.builder()
                                    .number("unknown")
                                    .normalizedNumber("__unknown__")
                                    .name(suggestedName)
                                    .build())));
        }
        String normalized = normalizeNumber(rawNumber);
        return contactCache.computeIfAbsent(normalized, n ->
                contactRepo.findByNormalizedNumber(n)
                        .orElseGet(() -> contactRepo.save(Contact.builder()
                                .number(rawNumber)
                                .normalizedNumber(normalized)
                                .name(suggestedName)
                                .build())));
    }

    private String normalizeNumber(String num) {
        String digits = num == null ? "" : num.replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.startsWith("1")) {
            digits = digits.substring(1);
        }
        return digits;
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

    private void processParts(Element parent, Message msg) {
        List<MessagePart> parts = new ArrayList<>();
        List<Map<String, Object>> mediaParts = new ArrayList<>();
        StringBuilder textAgg = new StringBuilder();

        NodeList partNodes = parent.getElementsByTagName("part");
        for (int i = 0; i < partNodes.getLength(); i++) {
            Element partEl = (Element) partNodes.item(i);
            String ct = partEl.getAttribute("ct");

            MessagePart part = new MessagePart();
            part.setMessage(msg);
            part.setSeq(i);
            part.setContentType(ct);
            part.setName(nullIfBlank(partEl.getAttribute("name")));
            part.setText(nullIfBlank(partEl.getAttribute("text")));

            if (part.getText() != null && "text/plain".equalsIgnoreCase(ct)) {
                textAgg.append(part.getText()).append(' ');
            }

            String data = partEl.getAttribute("data");
            if (data != null && !data.isBlank()) {
                saveMediaPart(data, part).ifPresent(path -> {
                    part.setFilePath(path);
                    if (!"text/plain".equalsIgnoreCase(ct) && !"application/smil".equalsIgnoreCase(ct)) {
                        String safeCt = (ct == null || ct.isBlank()) ? "application/octet-stream" : ct;
                        String safeName = part.getName() == null ? "" : part.getName();
                        Map<String, Object> mediaMap = new LinkedHashMap<>();
                        mediaMap.put("seq", part.getSeq());
                        mediaMap.put("contentType", safeCt);
                        mediaMap.put("name", safeName);
                        mediaMap.put("filePath", part.getFilePath());
                        mediaParts.add(mediaMap);
                    }
                });
            }
            parts.add(part);
        }

        if (textAgg.length() > 0 && (msg.getBody() == null || msg.getBody().isBlank())) {
            msg.setBody(textAgg.toString().trim());
        }
        if (!mediaParts.isEmpty()) {
            msg.setMedia(Map.of("parts", mediaParts));
        }
        msg.setParts(parts);
    }

    // Added HEIC/HEIF unsupported thumbnail handling
    private Optional<String> saveMediaPart(String base64, MessagePart part) {
        final Set<String> SUPPORTED_THUMB_TYPES = Set.of("image/jpeg","image/jpg","image/png","image/gif","image/bmp");
        final Set<String> UNSUPPORTED_IMAGE_TYPES = Set.of("image/heic","image/heif");

        try {
            String folder = Optional.ofNullable(part.getMessage().getId())
                    .map(Object::toString)
                    .orElse(UUID.randomUUID().toString());
            Path dir = MEDIA_ROOT.resolve(folder);
            Files.createDirectories(dir);

            String ext = guessExtension(part.getContentType(), part.getName());
            Path original = dir.resolve("part" + part.getSeq() + ext);
            Files.write(original, Base64.getDecoder().decode(base64));

            String ctLower = Optional.ofNullable(part.getContentType()).map(String::toLowerCase).orElse("");
            if (SUPPORTED_THUMB_TYPES.contains(ctLower)) {
                try {
                    Thumbnails.of(original.toFile())
                            .size(400, 400)
                            .outputFormat("jpg")
                            .outputQuality(0.80)
                            .toFile(dir.resolve("part" + part.getSeq() + "_thumb.jpg").toFile());
                } catch (Exception e) {
                    log.warn("Thumbnail failed: {}", original, e);
                }
            } else if (UNSUPPORTED_IMAGE_TYPES.contains(ctLower)) {
                createUnsupportedThumbnail(dir, part.getSeq());
            }

            return Optional.of(original.toString());
        } catch (Exception e) {
            log.error("Media save failed", e);
            return Optional.empty();
        }
    }

    // Generates a simple placeholder jpg saying NOT SUPPORTED
    private void createUnsupportedThumbnail(Path dir, int seq) {
        try {
            int w = 400, h = 400;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.DARK_GRAY);
            g.fillRect(0,0,w,h);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 32));
            String text = "HEIC";
            int tw = g.getFontMetrics().stringWidth(text);
            g.drawString(text, (w - tw)/2, h/2 - 10);
            g.setFont(new Font("SansSerif", Font.PLAIN, 20));
            String sub = "Not Supported";
            int sw = g.getFontMetrics().stringWidth(sub);
            g.drawString(sub, (w - sw)/2, h/2 + 25);
            g.dispose();
            ImageIO.write(img, "jpg", dir.resolve("part" + seq + "_thumb.jpg").toFile());
        } catch (Exception e) {
            log.warn("Failed to create unsupported thumbnail placeholder", e);
        }
    }

    private Stream<Element> nodeListToStream(NodeList list) {
        return IntStream.range(0, list.getLength())
                .mapToObj(list::item)
                .filter(Element.class::isInstance)
                .map(Element.class::cast);
    }

    private Instant parseInstant(String ms) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(ms));
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private int parseInt(String v, int def) {
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return def;
        }
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private record AddressInfo(String sender, String recipients) {}

    private AddressInfo parseAddresses(Element parent, int msgBox) {
        String sender = null;
        List<String> recipients = new ArrayList<>();
        NodeList addrNodes = parent.getElementsByTagName("addr");
        for (int i = 0; i < addrNodes.getLength(); i++) {
            Element addrEl = (Element) addrNodes.item(i);
            String type = addrEl.getAttribute("type");
            String address = addrEl.getAttribute("address");
            if (ADDR_TYPE_FROM.equals(type)) sender = address;
            else if (ADDR_TYPE_TO.equals(type)) recipients.add(address);
        }
        if (msgBox == MSG_BOX_SENT) {
            sender = SENDER_ME;
        } else if (msgBox == MSG_BOX_INBOX) {
            recipients = List.of(SENDER_ME);
        }
        return new AddressInfo(sender, String.join(",", recipients));
    }

    private static final Map<String,String> CONTENT_TYPE_TO_EXTENSION = Map.ofEntries(
            Map.entry("image/jpeg",".jpg"),
            Map.entry("image/jpg",".jpg"),
            Map.entry("image/png",".png"),
            Map.entry("image/gif",".gif"),
            Map.entry("image/webp",".webp"),
            Map.entry("image/bmp",".bmp"),
            Map.entry("image/heic",".heic"),
            Map.entry("image/heif",".heif"),
            Map.entry("video/mp4",".mp4"),
            Map.entry("video/3gpp",".3gp"),
            Map.entry("video/quicktime",".mov"),
            Map.entry("video/webm",".webm"),
            Map.entry("audio/mpeg",".mp3"),
            Map.entry("audio/amr",".amr"),
            Map.entry("audio/ogg",".ogg"),
            Map.entry("audio/mp4",".m4a"),
            Map.entry("audio/wav",".wav"),
            Map.entry("application/pdf",".pdf"),
            Map.entry("text/vcard",".vcf"),
            Map.entry("text/x-vcard",".vcf")
    );

    private String guessExtension(String ct, String name) {
        if (name != null) {
            int i = name.lastIndexOf('.');
            if (i >= 0) return name.substring(i).toLowerCase();
        }
        return Optional.ofNullable(ct)
                .map(String::toLowerCase)
                .map(CONTENT_TYPE_TO_EXTENSION::get)
                .orElse("");
    }
}
