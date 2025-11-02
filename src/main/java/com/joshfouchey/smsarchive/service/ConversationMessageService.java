package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.ConversationSummaryDto;
import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.dto.PagedResponse;
import com.joshfouchey.smsarchive.mapper.MessageMapper;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.ConversationMessageRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConversationMessageService {
    private final ConversationMessageRepository conversationMessageRepository;
    private final CurrentUserProvider currentUserProvider;

    public ConversationMessageService(ConversationMessageRepository conversationMessageRepository,
                                      CurrentUserProvider currentUserProvider) {
        this.conversationMessageRepository = conversationMessageRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<ConversationSummaryDto> getAllConversationSummaries() {
        var user = currentUserProvider.getCurrentUser();
        return conversationMessageRepository.findAllConversationSummaries(user);
    }

    @Transactional(readOnly = true)
    public PagedResponse<MessageDto> getMessagesByConversationId(Long conversationId,
                                                                 int page,
                                                                 int size,
                                                                 String sortDir) {
        if (size > 500) size = 500;
        if (size < 1) size = 1;
        Sort sort = Sort.by("timestamp");
        sort = "asc".equalsIgnoreCase(sortDir) ? sort.ascending() : sort.descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Message> result = conversationMessageRepository.findByConversationIdAndUser(conversationId, currentUserProvider.getCurrentUser(), pageable);
        List<MessageDto> content = result.getContent().stream().map(MessageMapper::toDto).toList();
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

