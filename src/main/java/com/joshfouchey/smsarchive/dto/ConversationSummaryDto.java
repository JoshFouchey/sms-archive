package com.joshfouchey.smsarchive.dto;

import com.joshfouchey.smsarchive.model.ConversationType;

import java.time.Instant;
import java.util.List;

public record ConversationSummaryDto(
        Long id,
        ConversationType type,
        String name,
        List<String> participantNames,
        Instant lastMessageAt,
        String lastMessagePreview,
        boolean lastMessageHasImage,
        Long unreadCount
) {}


