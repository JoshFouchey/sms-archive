package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.ContactSummaryDto;
import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Service
public class MessageService {


    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public List<ContactSummaryDto> getAllContactSummaries() {
        return messageRepository.findAllContactSummaries();
    }
}

