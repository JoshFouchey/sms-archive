package com.joshfouchey.smsarchive.dto;

public record QaRequest(
        String question,
        Long conversationId,
        Long contactId
) {}
