package com.joshfouchey.smsarchive.dto.api;

import com.joshfouchey.smsarchive.dto.MessagePartDto;
import com.joshfouchey.smsarchive.model.MessageDirection;

import java.time.Instant;
import java.util.List;

/**
 * Optimized DTO for bulk message loading (getAllConversationMessages endpoint).
 * Excludes unused fields to reduce JSON payload size for large conversations.
 */
public record ConversationMessagesDto(
        Long id,
        MessageDirection direction,
        Long senderContactId,
        String senderContactName,
        String senderContactNumber,
        String contactName,
        Instant timestamp,
        String body,
        List<MessagePartDto> parts
) {}
