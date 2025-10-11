package com.joshfouchey.smsarchive.dto;

public record AnalyticsSummaryDto(
        long totalContacts,
        long totalMessages,
        long totalImages
) {}
