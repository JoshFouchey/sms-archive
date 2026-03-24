package com.joshfouchey.smsarchive.dto;

public record SemanticSearchHit(
    MessageDto message,
    double similarity
) {}
