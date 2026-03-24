package com.joshfouchey.smsarchive.dto;

public record MergeSuggestion(
        Long entityId1,
        String entityName1,
        Long entityId2,
        String entityName2,
        String entityType,
        double similarity,
        String reason
) {}
