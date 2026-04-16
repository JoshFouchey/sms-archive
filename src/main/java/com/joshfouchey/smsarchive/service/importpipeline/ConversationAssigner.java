package com.joshfouchey.smsarchive.service.importpipeline;

import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.service.ConversationService;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class ConversationAssigner {

    private static final String UNKNOWN_NORMALIZED = "__unknown__";
    private static final String META_NORMALIZED_NUMBER = "_normalizedNumber";

    private final ConversationService conversationService;

    public ConversationAssigner(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public void assignConversationForSms(Message msg, String suggestedName, User user) {
        String normalized = resolveNormalizedNumberForSms(msg);
        if (normalized == null || normalized.isBlank() || UNKNOWN_NORMALIZED.equals(normalized)) {
            log.debug("Skipping conversation assignment - normalized invalid: {}", normalized);
            cleanupNormalizedMetadata(msg);
            return;
        }
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

    public void assignConversationForMultipart(Message msg, String threadKey, Set<String> participantNumbers, String suggestedName, User user) {
        if (participantNumbers == null || participantNumbers.isEmpty()) {
            log.debug("Skipping conversation assignment - no participant numbers");
            return;
        }
        if ((threadKey == null || threadKey.isBlank()) && participantNumbers.size() > 1) {
            threadKey = buildSyntheticThreadKey(participantNumbers);
        }
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

    private String resolveNormalizedNumberForSms(Message msg) {
        if (msg.getSenderContact() != null) {
            return msg.getSenderContact().getNormalizedNumber();
        }
        Map<String, Object> meta = msg.getMetadata();
        if (meta != null && meta.containsKey(META_NORMALIZED_NUMBER)) {
            return (String) meta.get(META_NORMALIZED_NUMBER);
        }
        return null;
    }

    private void cleanupNormalizedMetadata(Message msg) {
        Map<String, Object> meta = msg.getMetadata();
        if (meta == null) return;
        meta.remove(META_NORMALIZED_NUMBER);
        if (meta.isEmpty()) msg.setMetadata(null);
    }

    private boolean isInvalidConversation(Conversation convo) {
        return convo == null || convo.getId() == null;
    }

    private void updateConversationLastMessage(Conversation convo, Message msg) {
        if (convo.getLastMessageAt() == null || msg.getTimestamp().isAfter(convo.getLastMessageAt())) {
            convo.setLastMessageAt(msg.getTimestamp());
            conversationService.save(convo);
        }
    }

    String buildSyntheticThreadKey(Set<String> participantNumbers) {
        List<String> sorted = new ArrayList<>(participantNumbers);
        Collections.sort(sorted);
        return "GROUP:" + String.join(";", sorted);
    }
}
