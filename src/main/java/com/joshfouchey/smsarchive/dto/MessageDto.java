package com.joshfouchey.smsarchive.dto;
import java.time.Instant;

public record MessageDto(
        Long id,
        String protocol,
        String sender,
        String recipient,
        String contactName,
        Instant timestamp,
        String body
) {}
