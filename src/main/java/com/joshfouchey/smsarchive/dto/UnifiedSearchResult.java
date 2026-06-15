package com.joshfouchey.smsarchive.dto;

import java.util.List;
import java.util.Map;

public record UnifiedSearchResult(
        String query,
        String mode,
        List<UnifiedSearchHit> hits,
        int totalHits,
        Map<String, Object> diagnostics
) {
    public UnifiedSearchResult(String query, String mode, List<UnifiedSearchHit> hits, int totalHits) {
        this(query, mode, hits, totalHits, Map.of());
    }
}
