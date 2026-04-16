package com.joshfouchey.smsarchive.service.importpipeline;

import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.model.User;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class DuplicateDetector {

    private final MessageRepository messageRepo;

    public DuplicateDetector(MessageRepository messageRepo) {
        this.messageRepo = messageRepo;
    }

    public boolean isDuplicateInRunOrDb(Message msg, Set<String> seenKeys, List<Message> currentBatch) {
        String key = buildDuplicateKey(msg);
        if (seenKeys.contains(key)) {
            log.debug("Duplicate found in seenKeys: {}", key);
            return true;
        }
        if (isDuplicateInBatch(msg, currentBatch)) {
            log.debug("Duplicate found in current batch: {}", key);
            return true;
        }
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
        if (!msg1.getTimestamp().equals(msg2.getTimestamp())) {
            return false;
        }
        String body2Norm = msg2.getBody() == null ? "" : msg2.getBody().trim().toLowerCase();
        if (!body1Normalized.equals(body2Norm)) {
            return false;
        }
        UUID userId1 = msg1.getUser() != null ? msg1.getUser().getId() : null;
        UUID userId2 = msg2.getUser() != null ? msg2.getUser().getId() : null;
        if (userId1 != null && userId2 != null && !userId1.equals(userId2)) {
            return false;
        }
        Long convId1 = msg1.getConversation() != null ? msg1.getConversation().getId() : null;
        Long convId2 = msg2.getConversation() != null ? msg2.getConversation().getId() : null;
        if (convId1 != null && convId2 != null && !convId1.equals(convId2)) {
            return false;
        }
        if (msg1.getMsgBox() != msg2.getMsgBox()) {
            return false;
        }
        if (msg1.getProtocol() != msg2.getProtocol()) {
            return false;
        }
        return true;
    }

    public String buildDuplicateKey(Message msg) {
        String bodyNorm = msg.getBody() == null ? "" : msg.getBody().trim().toLowerCase();
        Long conversationId = msg.getConversation() == null ? null : msg.getConversation().getId();
        String conversationStr = (conversationId == null) ? "null" : conversationId.toString();
        UUID userId = msg.getUser() != null ? msg.getUser().getId() : null;
        String userStr = (userId == null) ? "null" : userId.toString();
        return userStr + "|" + conversationStr + "|" + msg.getTimestamp() + "|" + msg.getMsgBox() + "|" + msg.getProtocol() + "|" + bodyNorm;
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
}
