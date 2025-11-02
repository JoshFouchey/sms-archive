package com.joshfouchey.smsarchive.mapper;

import com.joshfouchey.smsarchive.dto.GalleryImageDto;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessagePart;

public final class GalleryMapper {

    private GalleryMapper() {}

    public static GalleryImageDto toDto(MessagePart part) {
        Message message = part.getMessage();
        Contact contact = message != null ? message.getContact() : null;

        return new GalleryImageDto(
                part.getId(),
                message != null ? message.getId() : null,
                normalizePath(part.getFilePath()),
                part.getContentType(),
                message != null ? message.getTimestamp() : null,
                contact != null ? contact.getId() : null,
                contact != null ? contact.getName() : null,
                contact != null ? contact.getNumber() : null
        );
    }

    private static String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        return raw.replace("\\", "/");
    }
}

