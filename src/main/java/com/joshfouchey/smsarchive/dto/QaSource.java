package com.joshfouchey.smsarchive.dto;

import java.time.Instant;

public record QaSource(
        Long messageId,
        String body,
        String contactName,
        Instant timestamp,
        double relevance
) {}
