package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.ConversationType;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.repository.ConversationRepository;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final CurrentUserProvider currentUserProvider;

    public ConversationService(ConversationRepository conversationRepository,
                               ContactRepository contactRepository,
                               CurrentUserProvider currentUserProvider) {
        this.conversationRepository = conversationRepository;
        this.contactRepository = contactRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Find or create a ONE_TO_ONE conversation for the current user and the given normalized number.
     * If a matching single-participant conversation already exists it is returned. Otherwise a new
     * Contact (if needed) and Conversation are created atomically.
     *
     * @param normalizedNumber canonical digits-only number
     * @param displayName optional display name to apply if creating new contact/conversation
     * @return persisted Conversation
     */
    @Transactional
    public Conversation findOrCreateOneToOne(String normalizedNumber, String displayName) {
        var user = currentUserProvider.getCurrentUser();

        // Attempt to find existing ONE_TO_ONE conversation with participant number
        var existingList = conversationRepository.findOneToOneByUserAndParticipant(user, normalizedNumber);
        if (!existingList.isEmpty()) {
            // If multiple due to legacy duplication, prefer the one with latest activity
            return existingList.stream()
                    .sorted((a,b) -> {
                        var at = Optional.ofNullable(a.getLastMessageAt()).orElse(java.time.Instant.EPOCH);
                        var bt = Optional.ofNullable(b.getLastMessageAt()).orElse(java.time.Instant.EPOCH);
                        return bt.compareTo(at);
                    })
                    .findFirst()
                    .get();
        }

        // Ensure contact exists (fallback to normalizedNumber if no displayName)
        Contact contact = contactRepository.findByUserAndNormalizedNumber(user, normalizedNumber)
                .orElseGet(() -> {
                    var c = com.joshfouchey.smsarchive.model.Contact.builder()
                            .user(user)
                            .number(normalizedNumber)
                            .normalizedNumber(normalizedNumber)
                            .name(displayName == null || displayName.isBlank() ? null : displayName)
                            .build();
                    return contactRepository.save(c);
                });

        // Create new conversation
        Conversation conversation = Conversation.builder()
                .user(user)
                .type(ConversationType.ONE_TO_ONE)
                .name(contact.getName() != null ? contact.getName() : contact.getNumber())
                .build();
        conversation.getParticipants().add(contact);
        return conversationRepository.save(conversation);
    }
}

