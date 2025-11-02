package com.joshfouchey.smsarchive.dto;

import com.joshfouchey.smsarchive.model.MessageDirection;
import com.joshfouchey.smsarchive.model.MessageProtocol;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MessageDto(
        Long id,
        MessageProtocol protocol,
        MessageDirection direction,
        Long senderContactId,              // null = current user (for OUTBOUND)
        String senderContactName,          // null = current user (for OUTBOUND)
        String senderContactNumber,        // null = current user (for OUTBOUND)
        String contactName,                // Primary contact (conversation counterparty)
        String contactNumber,
        String contactNormalizedNumber,
        Instant timestamp,
        String body,
        Integer msgBox,
        Instant deliveredAt,
        Instant readAt,
        Map<String, Object> media,
        Map<String, Object> metadata,
        List<MessagePartDto> parts,
        Instant createdAt,
        Instant updatedAt
) {}
