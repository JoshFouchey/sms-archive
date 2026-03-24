package com.joshfouchey.smsarchive.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record KgEntityDto(
    Long id,
    String canonicalName,
    String entityType,
    String description,
    Map<String, Object> metadata,
    List<String> aliases,
    Long linkedContactId,
    Instant createdAt
) {}
