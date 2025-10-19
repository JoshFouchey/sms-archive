package com.joshfouchey.smsarchive.dto;

public record ContactDto(
        Long id,
        String name,
        String number,
        String normalizedNumber
) {}

