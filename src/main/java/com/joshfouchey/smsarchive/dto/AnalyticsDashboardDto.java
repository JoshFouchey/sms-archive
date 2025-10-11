package com.joshfouchey.smsarchive.dto;

import java.util.List;

public record AnalyticsDashboardDto(
        AnalyticsSummaryDto summary,
        List<TopContactDto> topContacts,
        List<MessageCountPerDayDto> messagesPerDay
) {}

