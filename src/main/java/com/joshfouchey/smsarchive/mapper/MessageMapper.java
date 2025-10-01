package com.joshfouchey.smsarchive.mapper;

import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.model.Message;

public class MessageMapper {
    public static MessageDto toDto(Message msg) {
        return new MessageDto(
                msg.getId(),
                msg.getProtocol(),
                msg.getSender(),
                msg.getRecipient(),
                msg.getContactName(),
                msg.getTimestamp(),
                msg.getBody()
        );
    }
}
