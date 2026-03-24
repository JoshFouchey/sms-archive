package com.joshfouchey.smsarchive.dto;

import java.util.List;

public record UnifiedSearchResult(
        String query,
        String mode,
        List<UnifiedSearchHit> hits,
        int totalHits
) {}
