package com.joshfouchey.smsarchive.dto;

public record ContactMergeResultDto(
        Long primaryContactId,
        String primaryContactName,
        Long mergedContactId,
        String mergedContactName,
        int messagesTransferred,
        int duplicatesSkipped,
        int conversationsMerged,
        boolean success,
        String message
) {}

