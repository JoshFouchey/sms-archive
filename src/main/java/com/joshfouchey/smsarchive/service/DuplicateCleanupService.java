package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to clean up duplicate messages that were imported before the duplicate detection fix.
 * This is a one-time cleanup utility.
 */
@Service
@Slf4j
public class DuplicateCleanupService {

    private final MessageRepository messageRepository;

    public DuplicateCleanupService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /**
     * Identifies and removes duplicate messages, keeping only the first occurrence (by created_at).
     *
     * @return Map with cleanup statistics
     */
    @Transactional
    public Map<String, Object> removeDuplicates() {
        log.info("Starting duplicate message cleanup...");

        List<Message> allMessages = messageRepository.findAll();
        log.info("Loaded {} total messages", allMessages.size());

        // Group messages by duplicate key
        Map<String, List<Message>> messagesByKey = new HashMap<>();

        for (Message message : allMessages) {
            String key = buildDuplicateKey(message);
            messagesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(message);
        }

        // Find duplicates (groups with more than one message)
        List<Message> messagesToDelete = new ArrayList<>();
        int duplicateGroups = 0;

        for (Map.Entry<String, List<Message>> entry : messagesByKey.entrySet()) {
            List<Message> group = entry.getValue();
            if (group.size() > 1) {
                duplicateGroups++;
                // Sort by created_at to keep the oldest (first imported)
                group.sort(Comparator.comparing(Message::getCreatedAt));

                // Keep the first, delete the rest
                for (int i = 1; i < group.size(); i++) {
                    messagesToDelete.add(group.get(i));
                }

                log.debug("Found {} duplicates for key: {} (keeping id={}, deleting {} others)",
                        group.size(), entry.getKey(), group.get(0).getId(), group.size() - 1);
            }
        }

        // Delete the duplicates
        if (!messagesToDelete.isEmpty()) {
            log.info("Deleting {} duplicate messages from {} groups...", messagesToDelete.size(), duplicateGroups);
            messageRepository.deleteAll(messagesToDelete);
            log.info("Duplicate cleanup completed successfully");
        } else {
            log.info("No duplicates found");
        }

        // Return statistics
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMessages", allMessages.size());
        stats.put("duplicateGroups", duplicateGroups);
        stats.put("duplicatesDeleted", messagesToDelete.size());
        stats.put("remainingMessages", allMessages.size() - messagesToDelete.size());

        return stats;
    }

    /**
     * Preview duplicates without deleting them.
     *
     * @return Map with preview statistics and sample duplicates
     */
    @Transactional(readOnly = true)
    public Map<String, Object> previewDuplicates() {
        log.info("Previewing duplicate messages...");

        List<Message> allMessages = messageRepository.findAll();
        Map<String, List<Message>> messagesByKey = new HashMap<>();

        for (Message message : allMessages) {
            String key = buildDuplicateKey(message);
            messagesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(message);
        }

        // Find duplicates
        List<Map<String, Object>> duplicateGroups = new ArrayList<>();
        int totalDuplicates = 0;

        for (Map.Entry<String, List<Message>> entry : messagesByKey.entrySet()) {
            List<Message> group = entry.getValue();
            if (group.size() > 1) {
                totalDuplicates += group.size() - 1;

                // Add first 10 groups to preview
                if (duplicateGroups.size() < 10) {
                    Map<String, Object> groupInfo = new HashMap<>();
                    groupInfo.put("count", group.size());
                    groupInfo.put("timestamp", group.get(0).getTimestamp());
                    groupInfo.put("body", truncate(group.get(0).getBody(), 50));
                    groupInfo.put("ids", group.stream().map(Message::getId).collect(Collectors.toList()));
                    duplicateGroups.add(groupInfo);
                }
            }
        }

        Map<String, Object> preview = new HashMap<>();
        preview.put("totalMessages", allMessages.size());
        preview.put("duplicateGroups", duplicateGroups.size());
        preview.put("totalDuplicates", totalDuplicates);
        preview.put("sampleGroups", duplicateGroups);

        return preview;
    }

    private String buildDuplicateKey(Message msg) {
        String bodyNorm = msg.getBody() == null ? "" : msg.getBody().trim().toLowerCase();
        Long conversationId = msg.getConversation() == null ? null : msg.getConversation().getId();
        String conversationStr = (conversationId == null) ? "null" : conversationId.toString();
        UUID userId = msg.getUser() != null ? msg.getUser().getId() : null;
        String userStr = (userId == null) ? "null" : userId.toString();
        return userStr + "|" + conversationStr + "|" + msg.getTimestamp() + "|" + msg.getMsgBox() + "|" + msg.getProtocol() + "|" + bodyNorm;
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() <= maxLength ? str : str.substring(0, maxLength) + "...";
    }
}

