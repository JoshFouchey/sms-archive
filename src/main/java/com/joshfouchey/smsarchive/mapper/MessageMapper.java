package com.joshfouchey.smsarchive.mapper;

import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.dto.MessagePartDto;
import com.joshfouchey.smsarchive.dto.api.ConversationMessagesDto;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessagePart;

import java.util.List;

public final class MessageMapper {

    private MessageMapper() {}

    public static MessageDto toDto(Message msg) {
        // Get primary contact from conversation participants (first participant for 1:1, null for groups)
        Contact c = null;
        if (msg.getConversation() != null && msg.getConversation().getParticipants() != null
                && msg.getConversation().getParticipants().size() == 1) {
            c = msg.getConversation().getParticipants().iterator().next();
        }

        // Get conversation info
        String conversationName = null;
        Integer participantCount = null;
        if (msg.getConversation() != null) {
            conversationName = msg.getConversation().getName();
            participantCount = msg.getConversation().getParticipants() != null 
                ? msg.getConversation().getParticipants().size() 
                : 0;
        }

        Contact sender = msg.getSenderContact();
        List<MessagePartDto> parts = msg.getParts() == null ? List.of() : msg.getParts().stream()
                .map(p -> toPartDto(msg, p))
                .toList();
        return new MessageDto(
                msg.getId(),
                msg.getProtocol(),
                msg.getDirection(),
                sender != null ? sender.getId() : null,
                sender != null ? sender.getName() : null,
                sender != null ? sender.getNumber() : null,
                c != null ? c.getName() : null,
                c != null ? c.getNumber() : null,
                c != null ? c.getNormalizedNumber() : null,
                conversationName,
                participantCount,
                msg.getTimestamp(),
                msg.getBody(),
                msg.getMsgBox(),
                msg.getDeliveredAt(),
                msg.getReadAt(),
                msg.getMedia(),
                msg.getMetadata(),
                parts,
                msg.getCreatedAt(),
                msg.getUpdatedAt()
        );
    }

    // Map a part including contextual fields from its parent message (timestamp)
    private static MessagePartDto toPartDto(Message parent, MessagePart part) {
        return new MessagePartDto(
                part.getId(),
                parent.getId(),
                parent.getTimestamp(),
                normalizePath(part.getFilePath()),
                part.getContentType()
        );
    }

    /**
     * Convert Message entity to lightweight DTO for bulk operations.
     * Excludes unused fields to reduce JSON payload size.
     */
    public static ConversationMessagesDto toLightDto(Message msg) {
        // Get primary contact from conversation participants (for contactName field)
        Contact c = null;
        if (msg.getConversation() != null && msg.getConversation().getParticipants() != null
                && msg.getConversation().getParticipants().size() == 1) {
            c = msg.getConversation().getParticipants().iterator().next();
        }

        Contact sender = msg.getSenderContact();
        List<MessagePartDto> parts = msg.getParts() == null ? List.of() : msg.getParts().stream()
                .map(p -> toPartDto(msg, p))
                .toList();
        
        return new ConversationMessagesDto(
                msg.getId(),
                msg.getDirection(),
                sender != null ? sender.getId() : null,
                sender != null ? sender.getName() : null,
                sender != null ? sender.getNumber() : null,
                c != null ? c.getName() : null,
                msg.getTimestamp(),
                msg.getBody(),
                parts
        );
    }

    private static String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) return raw; // keep null/blank
        return raw.replace("\\", "/");
    }
}
