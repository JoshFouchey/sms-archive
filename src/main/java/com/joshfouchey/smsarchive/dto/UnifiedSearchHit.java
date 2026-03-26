package com.joshfouchey.smsarchive.dto;

public record UnifiedSearchHit(
        MessageDto message,
        double score,
        String source,
        Long conversationId,
        int moreFromConversation
) {
    // Compact constructor for backward compatibility
    public UnifiedSearchHit(MessageDto message, double score, String source) {
        this(message, score, source, null, 0);
    }
}
