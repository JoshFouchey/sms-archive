package com.joshfouchey.smsarchive.dto;

import java.time.Instant;

public class ContactSummaryDto {
    private String contactName;
    private Instant lastMessageTimestamp;
    private String lastMessagePreview;
    private boolean hasImage;

    public ContactSummaryDto(String contactName, Instant lastMessageTimestamp, String lastMessagePreview, boolean hasImage) {
        this.contactName = contactName;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.lastMessagePreview = lastMessagePreview;
        this.hasImage = hasImage;
    }

    public String getContactName() { return contactName; }
    public Instant getLastMessageTimestamp() { return lastMessageTimestamp; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public boolean isHasImage() { return hasImage; }
}
