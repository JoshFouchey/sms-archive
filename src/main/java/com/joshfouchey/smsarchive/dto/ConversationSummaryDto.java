package com.joshfouchey.smsarchive.dto;

import java.time.Instant;
import java.util.List;

public record ConversationSummaryDto(
        Long id,
        String name,
        List<String> participantNames,
        int participantCount,
        Instant lastMessageAt,
        String lastMessagePreview,
        boolean lastMessageHasImage,
        Long unreadCount
) {}


