// ImportService.java
package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import static java.util.Base64.*;

@Service
public class ImportService {
    private static final Path MEDIA_ROOT = Paths.get("media/messages");

    private final MessageRepository messageRepo;
    public ImportService(MessageRepository messageRepo) {
        this.messageRepo = messageRepo;
    }

    public int importFromXml(File xmlFile) throws Exception {
        int count = 0;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        // --- SMS ---
        NodeList smsNodes = doc.getElementsByTagName("sms");
        for (int i = 0; i < smsNodes.getLength(); i++) {
            Element el = (Element) smsNodes.item(i);

            Message msg = new Message();
            msg.setProtocol("SMS");
            msg.setTimestamp(Instant.ofEpochMilli(Long.parseLong(el.getAttribute("date"))));
            msg.setContactName(el.getAttribute("contact_name"));
            msg.setBody(el.getAttribute("body"));

            int type = Integer.parseInt(el.getAttribute("type")); // msg_box
            msg.setMsgBox(type);
            String address = el.getAttribute("address");

            if (type == 1) { // inbox → received
                msg.setSender(address);
                msg.setRecipient("me");
            } else if (type == 2) { // sent → outgoing
                msg.setSender("me");
                msg.setRecipient(address);
            } else {
                msg.setSender(address);
                msg.setRecipient(null);
            }

            messageRepo.save(msg);
            count++;
        }

        // --- MMS ---
        NodeList mmsNodes = doc.getElementsByTagName("mms");
        for (int i = 0; i < mmsNodes.getLength(); i++) {
            Element el = (Element) mmsNodes.item(i);

            Message msg = new Message();
            msg.setProtocol("MMS");
            msg.setContactName(el.getAttribute("contact_name"));
            msg.setTimestamp(Instant.ofEpochMilli(Long.parseLong(el.getAttribute("date"))));
            int msgBox = Integer.parseInt(el.getAttribute("msg_box"));
            msg.setMsgBox(msgBox);

            // --- Parse addresses ---
            String sender = null;
            // You might have multiple recipients in a group chat
            List<String> recipients = new ArrayList<>();
            NodeList addrs = el.getElementsByTagName("addr");

            for (int j = 0; j < addrs.getLength(); j++) {
                Element addrEl = (Element) addrs.item(j);
                String addr = addrEl.getAttribute("address");
                String type = addrEl.getAttribute("type");

                // Corrected Logic: 137=FROM (Sender), 151=TO (Recipient)
                if ("137".equals(type)) {
                    sender = addr; // This is the SENDER
                } else if ("151".equals(type)) {
                    recipients.add(addr); // This is a RECIPIENT
                }
            }

            // Replace your single 'recipient' string with a joined list
            // This handles both single and group messages.
            String recipient = String.join(",", recipients);

            // This removes your own number from the recipient list for sent messages,
            // as some backup tools include it.
            if (msgBox == 2) { // Sent message
                sender = "me";
                // exclude the "from" addr since that's you, keep recipients as-is
                recipient = String.join(",", recipients);
            } else if (msgBox == 1) { // Inbox
                // the fromAddr (type=137) is the sender
                // you are always the recipient
                recipient = "me";
            }


            msg.setSender(sender);
            msg.setRecipient(recipient);

            // --- Collect MMS parts ---
            StringBuilder textAggregate = new StringBuilder();
            List<MessagePart> parts = new ArrayList<>();
            List<Map<String, Object>> mediaList = new ArrayList<>();

            NodeList partNodes = el.getElementsByTagName("part");
            for (int j = 0; j < partNodes.getLength(); j++) {
                Element partEl = (Element) partNodes.item(j);

                String ct = partEl.getAttribute("ct");
                String text = partEl.getAttribute("text");
                String name = partEl.getAttribute("name");
                String data = partEl.getAttribute("data"); // raw base64/binary if present

                MessagePart part = new MessagePart();
                part.setContentType(ct);
                part.setName(name);
                part.setSeq(j);
                part.setMessage(msg);

                if ("text/plain".equals(ct) && text != null && !text.isEmpty()) {
                    part.setText(text);
                    textAggregate.append(text).append(" ");
                } else if (data != null && !data.isEmpty()) {
                    try {
                        // Use message id if already assigned, otherwise temp folder
                        String folderName = msg.getId() != null ? msg.getId().toString() : UUID.randomUUID().toString();
                        Path msgDir = MEDIA_ROOT.resolve(folderName);
                        Files.createDirectories(msgDir);

                        String ext = guessExtension(ct, name);
                        Path outFile = msgDir.resolve("part" + j + ext);

                        byte[] bytes = getDecoder().decode(data);
                        Files.write(outFile, bytes);

                        part.setFilePath(outFile.toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                parts.add(part);

                // Collect media metadata (optional)
                if (ct != null && !ct.isEmpty() && !"text/plain".equals(ct) && !"application/smil".equals(ct)) {
                    Map<String, Object> mediaEntry = new HashMap<>();
                    mediaEntry.put("contentType", ct);
                    mediaEntry.put("name", name);
                    mediaEntry.put("seq", j);
                    mediaEntry.put("filePath", part.getFilePath());
                    mediaList.add(mediaEntry);
                }
            }


            // --- Save aggregate fields on Message ---
            if (!textAggregate.isEmpty()) {
                msg.setBody(textAggregate.toString().trim());
            }
            if (!mediaList.isEmpty()) {
                msg.setMedia(Map.of("parts", mediaList));  // stored as JSON if using Hibernate + @Convert
            }

            msg.setParts(parts);
            messageRepo.save(msg);
            count++;
        }


        // --- RCS ---
        NodeList rcsNodes = doc.getElementsByTagName("rcs");
        for (int i = 0; i < rcsNodes.getLength(); i++) {
            Element el = (Element) rcsNodes.item(i);

            Message msg = new Message();
            msg.setProtocol("RCS");
            msg.setTimestamp(Instant.ofEpochMilli(Long.parseLong(el.getAttribute("date"))));
            msg.setContactName(el.getAttribute("contact_name"));

            int msgBox = Integer.parseInt(el.getAttribute("msg_box"));
            msg.setMsgBox(msgBox);

            // --- Parse addresses ---
            String sender = null;
            // You might have multiple recipients in a group chat
            List<String> recipients = new ArrayList<>();
            NodeList addrs = el.getElementsByTagName("addr");

            for (int j = 0; j < addrs.getLength(); j++) {
                Element addrEl = (Element) addrs.item(j);
                String addr = addrEl.getAttribute("address");
                String type = addrEl.getAttribute("type");

                // Corrected Logic: 137=FROM (Sender), 151=TO (Recipient)
                if ("137".equals(type)) {
                    sender = addr; // This is the SENDER
                } else if ("151".equals(type)) {
                    recipients.add(addr); // This is a RECIPIENT
                }
            }

            // Replace your single 'recipient' string with a joined list
            // This handles both single and group messages.
            String recipient = String.join(",", recipients);

            // This removes your own number from the recipient list for sent messages,
            // as some backup tools include it.
            if (msgBox == 2) { // Sent message
                sender = "me";
                // exclude the "from" addr since that's you, keep recipients as-is
                recipient = String.join(",", recipients);
            } else if (msgBox == 1) { // Inbox
                // the fromAddr (type=137) is the sender
                // you are always the recipient
                recipient = "me";
            }


            msg.setSender(sender);
            msg.setRecipient(recipient);

            // --- Collect RCS parts ---
            StringBuilder textAggregate = new StringBuilder();
            List<MessagePart> parts = new ArrayList<>();
            List<Map<String, Object>> mediaList = new ArrayList<>();

            NodeList partNodes = el.getElementsByTagName("part");
            for (int j = 0; j < partNodes.getLength(); j++) {
                Element partEl = (Element) partNodes.item(j);

                String ct = partEl.getAttribute("ct");
                String text = partEl.getAttribute("text");
                String name = partEl.getAttribute("name");
                String data = partEl.getAttribute("data"); // raw base64/binary if present

                MessagePart part = new MessagePart();
                part.setContentType(ct);
                part.setName(name);
                part.setSeq(j);
                part.setMessage(msg);

                if ("text/plain".equals(ct) && text != null && !text.isEmpty()) {
                    part.setText(text);
                    textAggregate.append(text).append(" ");
                } else if (data != null && !data.isEmpty()) {
                    try {
                        // Use message id if already assigned, otherwise temp folder
                        String folderName = msg.getId() != null ? msg.getId().toString() : UUID.randomUUID().toString();
                        Path msgDir = MEDIA_ROOT.resolve(folderName);
                        Files.createDirectories(msgDir);

                        String ext = guessExtension(ct, name);
                        Path outFile = msgDir.resolve("part" + j + ext);

                        byte[] bytes = Base64.getDecoder().decode(data);
                        Files.write(outFile, bytes);

                        part.setFilePath(outFile.toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                parts.add(part);

                // Collect media metadata (optional)
                if (ct != null && !ct.isEmpty() && !"text/plain".equals(ct) && !"application/smil".equals(ct)) {
                    Map<String, Object> mediaEntry = new HashMap<>();
                    mediaEntry.put("contentType", ct);
                    mediaEntry.put("name", name);
                    mediaEntry.put("seq", j);
                    mediaEntry.put("filePath", part.getFilePath());
                    mediaList.add(mediaEntry);
                }
            }


            // --- Save aggregate fields ---
            // Prefer parts over "body" attribute if present
            if (textAggregate.length() > 0) {
                msg.setBody(textAggregate.toString().trim());
            } else {
                msg.setBody(el.getAttribute("body"));
            }

            if (!mediaList.isEmpty()) {
                msg.setMedia(Map.of("parts", mediaList));
            }

            msg.setParts(parts);
            messageRepo.save(msg);
            count++;
        }
        return count;
    }

    private String guessExtension(String ct, String name) {
        // If original name has an extension, trust that first
        if (name != null && !name.isEmpty()) {
            int dot = name.lastIndexOf(".");
            if (dot >= 0) {
                return name.substring(dot).toLowerCase();
            }
        }

        if (ct == null) return "";

        switch (ct.toLowerCase()) {
            // --- Images ---
            case "image/jpeg":
            case "image/jpg": return ".jpg";
            case "image/png": return ".png";
            case "image/gif": return ".gif";
            case "image/webp": return ".webp";
            case "image/bmp": return ".bmp";
            case "image/heic": return ".heic";
            case "image/heif": return ".heif";
            case "image/tiff": return ".tiff";

            // --- Video ---
            case "video/mp4": return ".mp4";
            case "video/3gpp":
            case "video/3gp": return ".3gp";
            case "video/quicktime": return ".mov";
            case "video/x-msvideo": return ".avi";
            case "video/x-matroska": return ".mkv";
            case "video/webm": return ".webm";

            // --- Audio ---
            case "audio/mpeg": return ".mp3";
            case "audio/amr": return ".amr";
            case "audio/ogg": return ".ogg";
            case "audio/mp4": return ".m4a";
            case "audio/wav": return ".wav";
            case "audio/x-ms-wma": return ".wma";
            case "audio/flac": return ".flac";

            // --- Other docs / misc ---
            case "application/pdf": return ".pdf";
            case "application/zip": return ".zip";
            case "text/plain": return ".txt";
            case "text/html": return ".html";
            case "application/msword": return ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return ".docx";
            case "application/vnd.ms-excel": return ".xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": return ".xlsx";

            default: return "";
        }
    }


}
