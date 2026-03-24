package com.joshfouchey.smsarchive.dto;

import java.time.Instant;

public record KgTripleDto(
    Long id,
    Long subjectId,
    String subjectName,
    String subjectType,
    String predicate,
    Long objectId,
    String objectName,
    String objectType,
    String objectValue,
    Float confidence,
    Long sourceMessageId,
    String extractedText,
    Boolean isVerified,
    Boolean isNegated,
    Instant createdAt
) {}
