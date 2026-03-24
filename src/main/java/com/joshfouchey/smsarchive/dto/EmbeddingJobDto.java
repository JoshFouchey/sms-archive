package com.joshfouchey.smsarchive.dto;

import java.time.Instant;
import java.util.UUID;

public record EmbeddingJobDto(
    UUID id,
    String status,
    Long totalMessages,
    Long processed,
    Long failed,
    String modelName,
    double percentComplete,
    Instant startedAt,
    Instant completedAt,
    String errorMessage,
    Instant createdAt
) {}
