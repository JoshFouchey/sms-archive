package com.joshfouchey.smsarchive.dto;

import java.time.Instant;
import java.util.List;

public record AnalyticsDashboardDto(
        AnalyticsSummaryDto summary,
        List<TopContactDto> topContacts,
        List<MessageCountPerDayDto> messagesPerDay,
        Instant generatedAt
) {}

