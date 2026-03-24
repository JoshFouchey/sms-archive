package com.joshfouchey.smsarchive.dto;

public record UnifiedSearchHit(
        MessageDto message,
        double score,
        String source
) {}
