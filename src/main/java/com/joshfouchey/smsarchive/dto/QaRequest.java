package com.joshfouchey.smsarchive.dto;

public record QaRequest(
        String question,
        String mode,
        Long conversationId,
        Long contactId
) {}
