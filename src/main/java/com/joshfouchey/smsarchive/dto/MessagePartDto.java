package com.joshfouchey.smsarchive.dto;

import java.time.Instant;

public record MessagePartDto(
        Long id,
        Long messageId,
        String sender,
        String recipient,
        Instant timestamp,
        String filePath,
        String contentType
) {}
