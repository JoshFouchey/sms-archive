package com.joshfouchey.smsarchive.dto;

import java.util.List;

public record ResolutionResult(
        int autoMerged,
        int contactsLinked,
        List<MergeSuggestion> suggestions
) {}
