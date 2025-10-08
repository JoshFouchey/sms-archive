package com.joshfouchey.smsarchive.mapper;

import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Message;

public final class MessageMapper {

    private MessageMapper() {}

    public static MessageDto toDto(Message msg) {
        Contact c = msg.getContact();
        return new MessageDto(
                msg.getId(),
                msg.getProtocol(),
                msg.getDirection(),
                msg.getSender(),
                msg.getRecipient(),
                c != null ? c.getName() : null,
                c != null ? c.getNumber() : null,
                c != null ? c.getNormalizedNumber() : null,
                msg.getTimestamp(),
                msg.getBody(),
                msg.getMsgBox(),
                msg.getDeliveredAt(),
                msg.getReadAt(),
                msg.getMedia(),
                msg.getMetadata(),
                msg.getCreatedAt(),
                msg.getUpdatedAt()
        );
    }
}
