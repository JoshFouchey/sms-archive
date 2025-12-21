package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.ContactSummaryDto;
import com.joshfouchey.smsarchive.dto.MessageContextDto;
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

    @org.springframework.cache.annotation.Cacheable(value = "contactSummaries", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public List<ContactSummaryDto> getAllContactSummaries() {
        var user = currentUserProvider.getCurrentUser();
        return messageRepository.findAllContactSummaries(user.getId()).stream()
                .map(p -> new ContactSummaryDto(
                        p.getContactId(),
                        p.getContactName(),
                        p.getLastMessageTimestamp().toInstant(),
                        p.getLastMessagePreview(),
                        p.getHasImage()
                ))
                .toList();
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

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "messageContext", key = "#messageId + '_' + #before + '_' + #after")
    public MessageContextDto getMessageContext(Long messageId, int before, int after) {
        if (before < 0) before = 0; if (before > 500) before = 500;
        if (after < 0) after = 0; if (after > 500) after = 500;
        var user = currentUserProvider.getCurrentUser();
        var centerEntity = messageRepository.findByIdAndUser(messageId, user);
        if (centerEntity == null) { return null; }
        var conversationId = centerEntity.getConversation().getId();
        var centerTs = centerEntity.getTimestamp();
        var beforePage = PageRequest.of(0, before);
        var afterPage = PageRequest.of(0, after);
        var beforeEntities = before == 0 ? List.<Message>of() : messageRepository.findBeforeInConversation(conversationId, centerTs, user, beforePage);
        var afterEntities = after == 0 ? List.<Message>of() : messageRepository.findAfterInConversation(conversationId, centerTs, user, afterPage);
        var centerDto = MessageMapper.toDto(centerEntity);
        var beforeDtos = beforeEntities.stream().map(MessageMapper::toDto).toList();
        var afterDtos = afterEntities.stream().map(MessageMapper::toDto).toList();
        return new MessageContextDto(conversationId, centerDto, beforeDtos, afterDtos);
    }
}
