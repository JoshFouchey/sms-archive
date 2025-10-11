package com.joshfouchey.smsarchive.dto;

import java.time.Instant;

public record TopContactDto(
        Long contactId,
        String displayName,
        long messageCount,
        Instant lastMessageAt
) {}
