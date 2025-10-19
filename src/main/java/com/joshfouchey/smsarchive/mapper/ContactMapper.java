package com.joshfouchey.smsarchive.mapper;

import com.joshfouchey.smsarchive.dto.ContactDto;
import com.joshfouchey.smsarchive.model.Contact;

public final class ContactMapper {
    private ContactMapper() {}

    public static ContactDto toDto(Contact c) {
        return new ContactDto(c.getId(), c.getName(), c.getNumber(), c.getNormalizedNumber());
    }
}

