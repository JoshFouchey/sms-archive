package com.joshfouchey.smsarchive.dto;

import java.time.Instant;

public record GalleryImageDto(
        Long id,
        Long messageId,
        String filePath,
        String contentType,
        Instant timestamp,
        Long contactId,
        String contactName,
        String contactNumber
) {}