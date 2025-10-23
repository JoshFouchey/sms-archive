package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.ContactSummaryDto;
import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.dto.PagedResponse;
import com.joshfouchey.smsarchive.mapper.MessageMapper;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final CurrentUserProvider currentUserProvider;

    public MessageService(MessageRepository messageRepository, CurrentUserProvider currentUserProvider) {
        this.messageRepository = messageRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<ContactSummaryDto> getAllContactSummaries() {
        var user = currentUserProvider.getCurrentUser();
        return messageRepository.findAllContactSummaries(user);
    }

    @Transactional(readOnly = true)
    public PagedResponse<MessageDto> getMessagesByContactId(Long contactId,
                                                            int page,
                                                            int size,
                                                            String sortDir) {
        if (size > 500) size = 500;
        if (size < 1) size = 1;
        Sort sort = Sort.by("timestamp");
        sort = "asc".equalsIgnoreCase(sortDir) ? sort.ascending() : sort.descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Message> result = messageRepository.findByContactIdAndUser(contactId, currentUserProvider.getCurrentUser(), pageable);
        List<MessageDto> content = result.getContent().stream()
                .map(MessageMapper::toDto)
                .toList();
        return new PagedResponse<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }
}
