package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.ConversationSummaryDto;
import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.dto.PagedResponse;
import com.joshfouchey.smsarchive.mapper.MessageMapper;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.ConversationRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ContactRepository contactRepository;
    private final CurrentUserProvider currentUserProvider;

    public ConversationService(ConversationRepository conversationRepository,
                              MessageRepository messageRepository,
                              ContactRepository contactRepository,
                              CurrentUserProvider currentUserProvider) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.contactRepository = contactRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryDto> getAllConversations() {
        var user = currentUserProvider.getCurrentUser();
        List<Conversation> conversations = conversationRepository.findAllByUserOrderByLastMessage(user);

        return conversations.stream()
                .map(this::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<MessageDto> getConversationMessages(Long conversationId,
                                                             int page,
                                                             int size,
                                                             String sortDir) {
        if (size > 500) size = 500;
        if (size < 1) size = 1;

        var user = currentUserProvider.getCurrentUser();

        // Verify conversation belongs to user
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        Sort sort = Sort.by("timestamp");
        sort = "asc".equalsIgnoreCase(sortDir) ? sort.ascending() : sort.descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Message> result = messageRepository.findByConversationIdAndUser(conversationId, user, pageable);

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

    private ConversationSummaryDto toSummaryDto(Conversation conv) {
        // Get participant names
        List<String> participantNames = conv.getParticipants().stream()
                .map(contact -> contact.getName() != null ? contact.getName() : contact.getNumber())
                .collect(Collectors.toList());

        // Get last message details
        var user = currentUserProvider.getCurrentUser();
        Message lastMessage = messageRepository.findLastMessageByConversation(conv.getId(), user);

        String lastMessagePreview = null;
        boolean lastMessageHasImage = false;

        if (lastMessage != null) {
            lastMessagePreview = lastMessage.getBody() != null && !lastMessage.getBody().isEmpty()
                    ? lastMessage.getBody().substring(0, Math.min(200, lastMessage.getBody().length()))
                    : "";
            lastMessageHasImage = lastMessage.getParts() != null &&
                    lastMessage.getParts().stream()
                            .anyMatch(part -> part.getContentType() != null &&
                                    part.getContentType().startsWith("image/"));
        }

        return new ConversationSummaryDto(
                conv.getId(),
                conv.getName(),
                participantNames,
                conv.getParticipants().size(),
                conv.getLastMessageAt(),
                lastMessagePreview,
                lastMessageHasImage,
                0L // TODO: implement unread count if needed
        );
    }

    // Methods for ImportService

    @Transactional
    public Conversation findOrCreateOneToOne(String normalizedNumber, String suggestedName) {
        var user = currentUserProvider.getCurrentUser();
        return findOrCreateOneToOneForUser(user, normalizedNumber, suggestedName);
    }

    @Transactional
    public Conversation findOrCreateGroup(String threadKey, Set<String> participantNumbers, String suggestedName) {
        var user = currentUserProvider.getCurrentUser();
        return findOrCreateGroupForUser(user, threadKey, participantNumbers, suggestedName);
    }

    @Transactional
    public Conversation findOrCreateOneToOneForUser(User user, String normalizedNumber, String suggestedName) {
        // Find or create contact
        Contact contact = contactRepository.findByUserAndNormalizedNumber(user, normalizedNumber)
                .orElseGet(() -> {
                    Contact c = new Contact();
                    c.setUser(user);
                    c.setNormalizedNumber(normalizedNumber);
                    c.setNumber(normalizedNumber);
                    c.setName(suggestedName != null && !suggestedName.isBlank() ? suggestedName : null);
                    return contactRepository.save(c);
                });

        // Find existing conversation with single participant
        List<Conversation> existing = conversationRepository.findByUserAndSingleParticipant(user, normalizedNumber);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        // Create new conversation with single participant
        Conversation conv = new Conversation();
        conv.setUser(user);
        conv.setName(contact.getName() != null ? contact.getName() : contact.getNumber());
        conv.getParticipants().add(contact);
        return conversationRepository.save(conv);
    }

    @Transactional
    public Conversation findOrCreateGroupForUser(User user, String threadKey, Set<String> participantNumbers, String suggestedName) {
        // Try to find existing conversation by thread key
        if (threadKey != null && !threadKey.isBlank()) {
            var existing = conversationRepository.findByThreadKey(user, threadKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // Find or create contacts for all participants
        Set<Contact> contacts = new HashSet<>();
        for (String normalized : participantNumbers) {
            Contact contact = contactRepository.findByUserAndNormalizedNumber(user, normalized)
                    .orElseGet(() -> {
                        Contact c = new Contact();
                        c.setUser(user);
                        c.setNormalizedNumber(normalized);
                        c.setNumber(normalized);
                        return contactRepository.save(c);
                    });
            contacts.add(contact);
        }

        // Create new conversation
        Conversation conv = new Conversation();
        conv.setUser(user);
        conv.setThreadKey(threadKey);
        conv.setName(suggestedName != null && !suggestedName.isBlank() ? suggestedName : "Group Chat");
        conv.setParticipants(contacts);
        return conversationRepository.save(conv);
    }

    @Transactional
    public Conversation save(Conversation conversation) {
        return conversationRepository.save(conversation);
    }

    @Transactional
    public void deleteConversationById(Long conversationId) {
        var user = currentUserProvider.getCurrentUser();
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        // Fetch messages for this conversation
        List<Message> messages = messageRepository.findAllByConversationIdAndUser(conversationId, user);
        // Delete messages (parts cascade via orphanRemoval)
        if (!messages.isEmpty()) {
            messageRepository.deleteAll(messages);
            messageRepository.flush();
        }
        // Finally delete the conversation
        conversationRepository.delete(conversation);
    }
}
