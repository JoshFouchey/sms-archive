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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImportService {

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

                MessagePart part = new MessagePart();
                part.setContentType(ct);
                part.setText(text);
                part.setName(name);
                part.setSeq(j);
                part.setMessage(msg);

                parts.add(part);

                if ("text/plain".equals(ct) && text != null && !text.isEmpty()) {
                    textAggregate.append(text).append(" ");
                } else if (ct != null && !ct.isEmpty() && !"application/smil".equals(ct)) {
                    // treat all non-text parts as media
                    Map<String, Object> mediaEntry = new HashMap<>();
                    mediaEntry.put("contentType", ct);
                    mediaEntry.put("name", name);
                    mediaEntry.put("seq", j);
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

                MessagePart part = new MessagePart();
                part.setContentType(ct);
                part.setText(text);
                part.setName(name);
                part.setSeq(j);
                part.setMessage(msg);

                parts.add(part);

                if ("text/plain".equals(ct) && text != null && !text.isEmpty()) {
                    textAggregate.append(text).append(" ");
                } else if (ct != null && !ct.isEmpty()) {
                    Map<String, Object> mediaEntry = new HashMap<>();
                    mediaEntry.put("contentType", ct);
                    mediaEntry.put("name", name);
                    mediaEntry.put("seq", j);
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
}
