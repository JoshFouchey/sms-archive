// ImportService.java
package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j; // Using Lombok for logging is a good practice
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import net.coobird.thumbnailator.Thumbnails;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class ImportService {

    private static final Path MEDIA_ROOT = Paths.get("media/messages");
    private static final String SENDER_ME = "me";
    private static final String ADDR_TYPE_FROM = "137"; // Sender
    private static final String ADDR_TYPE_TO = "151";   // Recipient
    private static final int MSG_BOX_INBOX = 1;
    private static final int MSG_BOX_SENT = 2;

    private final MessageRepository messageRepo;

    public ImportService(MessageRepository messageRepo) {
        this.messageRepo = messageRepo;
    }

    /**
     * Imports SMS, MMS, and RCS messages from an XML backup file.
     */
    public int importFromXml(File xmlFile) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        List<Message> allMessages = new ArrayList<>();
        allMessages.addAll(processSmsNodes(doc.getElementsByTagName("sms")));
        allMessages.addAll(processMultipartNodes(doc.getElementsByTagName("mms"), "MMS"));
        allMessages.addAll(processMultipartNodes(doc.getElementsByTagName("rcs"), "RCS"));

        if (!allMessages.isEmpty()) {
            messageRepo.saveAll(allMessages);
        }

        return allMessages.size();
    }

    /**
     * Processes all <sms> nodes from the XML document.
     */
    private List<Message> processSmsNodes(NodeList smsNodes) {
        return nodeListToStream(smsNodes)
                .map(el -> {
                    Message msg = new Message();
                    msg.setProtocol("SMS");
                    msg.setTimestamp(Instant.ofEpochMilli(Long.parseLong(el.getAttribute("date"))));
                    msg.setContactName(el.getAttribute("contact_name"));
                    msg.setBody(el.getAttribute("body"));

                    int msgBox = Integer.parseInt(el.getAttribute("type"));
                    msg.setMsgBox(msgBox);

                    String address = el.getAttribute("address");
                    if (msgBox == MSG_BOX_INBOX) {
                        msg.setSender(address);
                        msg.setRecipient(SENDER_ME);
                    } else if (msgBox == MSG_BOX_SENT) {
                        msg.setSender(SENDER_ME);
                        msg.setRecipient(address);
                    } else {
                        msg.setSender(address);
                        msg.setRecipient(null); // Or some other default
                    }
                    return msg;
                })
                .collect(Collectors.toList());
    }

    /**
     * Processes all multipart message nodes (<mms> or <rcs>).
     * This method abstracts the shared logic between MMS and RCS processing.
     */
    private List<Message> processMultipartNodes(NodeList multipartNodes, String protocol) {
        return nodeListToStream(multipartNodes)
                .map(el -> {
                    Message msg = new Message();
                    msg.setProtocol(protocol);
                    msg.setTimestamp(Instant.ofEpochMilli(Long.parseLong(el.getAttribute("date"))));
                    msg.setContactName(el.getAttribute("contact_name"));

                    int msgBox = Integer.parseInt(el.getAttribute("msg_box"));
                    msg.setMsgBox(msgBox);

                    AddressInfo addresses = parseAddresses(el, msgBox);
                    msg.setSender(addresses.sender());
                    msg.setRecipient(addresses.recipient());

                    processParts(el, msg);

                    // RCS can have a 'body' attribute as a fallback
                    if ("RCS".equals(protocol) && (msg.getBody() == null || msg.getBody().isEmpty())) {
                        msg.setBody(el.getAttribute("body"));
                    }

                    return msg;
                })
                .collect(Collectors.toList());
    }

    /**
     * Parses the <addr> sub-elements to determine sender and recipients.
     */
    private AddressInfo parseAddresses(Element parent, int msgBox) {
        String sender = null;
        List<String> recipients = new ArrayList<>();

        NodeList addrNodes = parent.getElementsByTagName("addr");
        for (int i = 0; i < addrNodes.getLength(); i++) {
            Element addrEl = (Element) addrNodes.item(i);
            String type = addrEl.getAttribute("type");
            String address = addrEl.getAttribute("address");

            if (ADDR_TYPE_FROM.equals(type)) {
                sender = address; // This is now perfectly valid
            } else if (ADDR_TYPE_TO.equals(type)) {
                recipients.add(address);
            }
        }

        String finalSender = sender;
        String finalRecipients = String.join(",", recipients);

        if (msgBox == MSG_BOX_SENT) {
            finalSender = SENDER_ME;
        } else if (msgBox == MSG_BOX_INBOX) {
            finalRecipients = SENDER_ME;
        }

        return new AddressInfo(finalSender, finalRecipients);
    }

    /**
     * Processes the <part> sub-elements of a multipart message.
     * This handles text aggregation and saving media attachments.
     */
    private void processParts(Element parent, Message msg) {
        List<MessagePart> parts = new ArrayList<>();
        List<Map<String, Object>> mediaList = new ArrayList<>();
        StringBuilder textAggregate = new StringBuilder();

        NodeList partNodes = parent.getElementsByTagName("part");
        for (int i = 0; i < partNodes.getLength(); i++) {
            Element partEl = (Element) partNodes.item(i);
            String contentType = partEl.getAttribute("ct");

            MessagePart part = new MessagePart();
            part.setContentType(contentType);
            part.setName(partEl.getAttribute("name"));
            part.setSeq(i);
            part.setMessage(msg);

            String text = partEl.getAttribute("text");
            if ("text/plain".equals(contentType) && text != null && !text.isEmpty()) {
                part.setText(text);
                textAggregate.append(text).append(" ");
            }

            String data = partEl.getAttribute("data");
            if (data != null && !data.isEmpty()) {
                saveMediaPart(data, part).ifPresent(filePath -> {
                    part.setFilePath(filePath);

                    // Collect metadata for media parts
                    if (!"text/plain".equals(contentType) && !"application/smil".equals(contentType)) {
                        mediaList.add(Map.of(
                                "contentType", contentType,
                                "name", part.getName(),
                                "seq", part.getSeq(),
                                "filePath", filePath
                        ));
                    }
                });
            }
            parts.add(part);
        }

        if (textAggregate.length() > 0) {
            msg.setBody(textAggregate.toString().trim());
        }
        if (!mediaList.isEmpty()) {
            msg.setMedia(Map.of("parts", mediaList));
        }
        msg.setParts(parts);
    }

    /**
     * Decodes Base64 data and saves it to a file, returning the file path.
     */
    private Optional<String> saveMediaPart(String base64Data, MessagePart part) {
        final List<String> SUPPORTED_THUMBNAIL_TYPES = List.of(
                "image/jpeg", "image/jpg", "image/png", "image/gif", "image/bmp"
        );

        try {
            // --- 1. Prepare directory ---
            String folderName = Optional.ofNullable(part.getMessage().getId())
                    .map(Object::toString)
                    .orElse(UUID.randomUUID().toString());

            Path msgDir = MEDIA_ROOT.resolve(folderName);
            Files.createDirectories(msgDir);

            // --- 2. Save original file ---
            String ext = guessExtension(part.getContentType(), part.getName());
            Path originalFile = msgDir.resolve("part" + part.getSeq() + ext);
            byte[] bytes = Base64.getDecoder().decode(base64Data);
            Files.write(originalFile, bytes);

            part.setFilePath(originalFile.toString());

            // --- 3. Determine thumbnail path (no schema change required) ---
            Path thumbFile = msgDir.resolve("part" + part.getSeq() + "_thumb.jpg");

            if (SUPPORTED_THUMBNAIL_TYPES.contains(part.getContentType().toLowerCase())) {
                // --- 4. Create thumbnail with Thumbnailator ---
                try {
                    Thumbnails.of(originalFile.toFile())
                            .size(400, 400)
                            .outputFormat("jpg")
                            .outputQuality(0.8)
                            .toFile(thumbFile.toFile());

                    log.info("✅ Created thumbnail: {}", thumbFile);
                } catch (Exception e) {
                    log.warn("⚠️ Failed to create thumbnail for {}. Using original image.", originalFile.getFileName(), e);
                    thumbFile = originalFile; // fallback to original
                }
            } else {
                // Unsupported format (HEIC, etc.)
                log.debug("Skipping thumbnail for unsupported format: {}", part.getContentType());
                thumbFile = originalFile;
            }

            // --- 5. Store or infer thumbnail path ---
            // Option 1: Store directly in DB if `thumbnailPath` exists
            // part.setThumbnailPath(thumbFile.toString());

            // Option 2: Don’t change schema, infer it on demand:
            // return Optional.of(originalFile.toString());

            return Optional.of(originalFile.toString());

        } catch (Exception e) {
            log.error("❌ Failed to save media part", e);
            return Optional.empty();
        }
    }

    /**
     * A simple data carrier for sender/recipient info. A Java Record is perfect for this.
     */
    private record AddressInfo(String sender, String recipient) {}

    /**
     * Utility to convert a NodeList to a modern Java Stream of Elements.
     */
    private Stream<Element> nodeListToStream(NodeList list) {
        return IntStream.range(0, list.getLength())
                .mapToObj(list::item)
                .filter(Element.class::isInstance)
                .map(Element.class::cast);
    }

    /**
     * Guesses a file extension based on content type or filename.
     * Refactored to use a Map for better organization.
     */
    private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION_MAP = Map.ofEntries(
            // --- Images ---
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/jpg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/gif", ".gif"),
            Map.entry("image/webp", ".webp"),
            Map.entry("image/bmp", ".bmp"),
            Map.entry("image/heic", ".heic"), // High Efficiency Image Format (Apple)
            Map.entry("image/heif", ".heif"),

            // --- Video ---
            Map.entry("video/mp4", ".mp4"),
            Map.entry("video/3gpp", ".3gp"), // Common for MMS
            Map.entry("video/quicktime", ".mov"), // Common from iPhones
            Map.entry("video/webm", ".webm"),

            // --- Audio ---
            Map.entry("audio/mpeg", ".mp3"),
            Map.entry("audio/amr", ".amr"), // Adaptive Multi-Rate (Voice notes)
            Map.entry("audio/ogg", ".ogg"),
            Map.entry("audio/mp4", ".m4a"), // M4A is an audio-only MP4
            Map.entry("audio/wav", ".wav"),

            // --- Other Common Files ---
            Map.entry("application/pdf", ".pdf"),
            Map.entry("text/vcard", ".vcf"), // Virtual Contact File
            Map.entry("text/x-vcard", ".vcf")
    );

    private String guessExtension(String ct, String name) {
        if (name != null) {
            int dotIndex = name.lastIndexOf(".");
            if (dotIndex >= 0) {
                return name.substring(dotIndex).toLowerCase();
            }
        }
        return Optional.ofNullable(ct)
                .map(String::toLowerCase)
                .map(CONTENT_TYPE_TO_EXTENSION_MAP::get)
                .orElse(""); // Default to no extension
    }
}