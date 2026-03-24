package com.joshfouchey.smsarchive.dto;

public record EmbeddingStatsDto(
    long totalMessages,
    long embeddedMessages,
    double percentComplete,
    String modelName
) {}
