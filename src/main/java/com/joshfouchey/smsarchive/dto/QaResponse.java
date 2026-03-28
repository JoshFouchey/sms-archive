package com.joshfouchey.smsarchive.dto;

import java.util.List;

public record QaResponse(
        String intent,
        String answer,
        List<QaSource> sources,
        Object analyticsData,
        UnifiedSearchResult searchResults,
        long processingTimeMs
) {
    public static QaResponse analytics(String answer, Object data, long ms) {
        return new QaResponse("ANALYTICS", answer, List.of(), data, null, ms);
    }

    public static QaResponse search(UnifiedSearchResult results, long ms) {
        return new QaResponse("SEARCH", null, List.of(), null, results, ms);
    }
}
