package com.joshfouchey.smsarchive.dto;

import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

@Getter
@ToString
public class ConversationSummaryDto {
    private final Long id;
    private final String type; // SINGLE or GROUP
    private final String displayName;
    private final long participantCount;
    private final Instant lastTimestamp;
    private final String lastPreviewText;
    private final boolean hasImage;

    public ConversationSummaryDto(Long id,
                                   String type,
                                   String displayName,
                                   Long participantCount,
                                   Instant lastTimestamp,
                                   String lastPreviewText,
                                   Boolean hasImage) {
        this.id = id;
        this.type = type;
        this.displayName = displayName;
        this.participantCount = participantCount == null ? 0L : participantCount;
        this.lastTimestamp = lastTimestamp;
        this.lastPreviewText = lastPreviewText;
        this.hasImage = hasImage != null && hasImage;
    }
}

