package com.joshfouchey.smsarchive.dto;

import java.util.List;

public class MessageContextDto {
    public long conversationId;
    public MessageDto center;
    public List<MessageDto> before;
    public List<MessageDto> after;

    public MessageContextDto(long conversationId, MessageDto center, List<MessageDto> before, List<MessageDto> after) {
        this.conversationId = conversationId;
        this.center = center;
        this.before = before;
        this.after = after;
    }
}

