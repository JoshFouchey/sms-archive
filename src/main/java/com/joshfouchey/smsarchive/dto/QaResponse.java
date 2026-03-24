package com.joshfouchey.smsarchive.dto;

import java.util.List;

public record QaResponse(
        String intent,
        String answer,
        List<QaSource> sources,
        List<KgTripleDto> kgFacts,
        Object analyticsData,
        UnifiedSearchResult searchResults,
        long processingTimeMs
) {
    public static QaResponse factual(String answer, List<QaSource> sources,
                                     List<KgTripleDto> kgFacts,
                                     UnifiedSearchResult searchResults, long ms) {
        return new QaResponse("FACTUAL", answer, sources, kgFacts, null, searchResults, ms);
    }

    public static QaResponse analytics(String answer, Object data, long ms) {
        return new QaResponse("ANALYTICS", answer, List.of(), List.of(), data, null, ms);
    }

    public static QaResponse search(UnifiedSearchResult results, long ms) {
        return new QaResponse("SEARCH", null, List.of(), List.of(), null, results, ms);
    }
}
