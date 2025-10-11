package com.joshfouchey.smsarchive.dto;

public record TopContactDto(
        Long contactId,
        String displayName,
        long messageCount
) {}
