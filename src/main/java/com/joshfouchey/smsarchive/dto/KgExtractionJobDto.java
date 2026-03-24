package com.joshfouchey.smsarchive.dto;

import java.time.Instant;
import java.util.UUID;

public record KgExtractionJobDto(
    UUID id,
    String status,
    Long totalMessages,
    Long processed,
    Long triplesFound,
    Long entitiesFound,
    double percentComplete,
    String modelName,
    Instant startedAt,
    Instant completedAt,
    String errorMessage,
    Instant createdAt
) {}
