package com.joshfouchey.smsarchive.dto;

import java.util.List;

public record SemanticSearchResult(
    String query,
    List<SemanticSearchHit> hits,
    int totalHits
) {}
