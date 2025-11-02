package com.joshfouchey.smsarchive.mapper;

import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.dto.MessagePartDto;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessagePart;

import java.util.List;

public final class MessageMapper {

    private MessageMapper() {}

    public static MessageDto toDto(Message msg) {
        Contact c = msg.getContact();
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

    private static String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) return raw; // keep null/blank
        return raw.replace("\\", "/");
    }
}
