package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.ConversationType;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.ConversationRepository;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
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
        return findOrCreateOneToOneForUser(user, normalizedNumber, displayName);
    }

    @Transactional
    public Conversation findOrCreateOneToOneForUser(User user, String normalizedNumber, String displayName) {
        log.debug("findOrCreateOneToOneForUser: user={}, normalizedNumber={}, displayName={}",
                user.getId(), normalizedNumber, displayName);

        var existingList = conversationRepository.findOneToOneByUserAndParticipant(user, normalizedNumber);
        if (!existingList.isEmpty()) {
            // If multiple due to legacy duplication, prefer the one with latest activity
            Conversation existing = existingList.stream()
                    .sorted((a,b) -> {
                        var at = Optional.ofNullable(a.getLastMessageAt()).orElse(java.time.Instant.EPOCH);
                        var bt = Optional.ofNullable(b.getLastMessageAt()).orElse(java.time.Instant.EPOCH);
                        return bt.compareTo(at);
                    })
                    .findFirst()
                    .get();
            log.debug("Found existing conversation: id={}", existing.getId());
            return existing;
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
                    Contact saved = contactRepository.save(c);
                    log.debug("Created new contact: id={}, normalizedNumber={}", saved.getId(), normalizedNumber);
                    return saved;
                });

        // Create new conversation
        Conversation conversation = Conversation.builder()
                .user(user)
                .type(ConversationType.ONE_TO_ONE)
                .name(contact.getName() != null ? contact.getName() : contact.getNumber())
                .build();
        conversation.getParticipants().add(contact);
        Conversation saved = conversationRepository.save(conversation);
        log.debug("Created new conversation: id={}, participants={}", saved.getId(), saved.getParticipants().size());
        return saved;
    }

    /**
     * Save a conversation entity (useful for updating lastMessageAt during import).
     */
    @Transactional
    public Conversation save(Conversation conversation) {
        return conversationRepository.save(conversation);
    }

    /**
     * Find or create a GROUP conversation using an external thread key (e.g., MMS/RCS address) and the set of
     * normalized participant numbers. Missing contacts will be created. Existing conversation participants are
     * merged (new ones added).
     */
    @Transactional
    public Conversation findOrCreateGroup(String threadKey, Set<String> participantNormalizedNumbers, String suggestedName) {
        var user = currentUserProvider.getCurrentUser();
        return findOrCreateGroupForUser(user, threadKey, participantNormalizedNumbers, suggestedName);
    }

    @Transactional
    public Conversation findOrCreateGroupForUser(User user, String threadKey, Set<String> participantNormalizedNumbers, String suggestedName) {
        var existingOpt = conversationRepository.findGroupByThreadKey(user, threadKey);
        if (existingOpt.isPresent()) {
            Conversation convo = existingOpt.get();
            // ensure all participants present
            var existingNumbers = convo.getParticipants().stream().map(Contact::getNormalizedNumber).collect(Collectors.toSet());
            for (String pn : participantNormalizedNumbers) {
                if (!existingNumbers.contains(pn)) {
                    Contact c = contactRepository.findByUserAndNormalizedNumber(user, pn)
                            .orElseGet(() -> contactRepository.save(Contact.builder()
                                    .user(user)
                                    .number(pn)
                                    .normalizedNumber(pn)
                                    .name(null)
                                    .build()));
                    convo.getParticipants().add(c);
                }
            }
            return conversationRepository.save(convo); // update participants if modified
        }
        // Build new conversation
        Conversation convo = Conversation.builder()
                .user(user)
                .type(ConversationType.GROUP)
                .threadKey(threadKey)
                .name(suggestedName == null || suggestedName.isBlank() ? threadKey : suggestedName)
                .build();
        for (String pn : participantNormalizedNumbers) {
            Contact c = contactRepository.findByUserAndNormalizedNumber(user, pn)
                    .orElseGet(() -> contactRepository.save(Contact.builder()
                            .user(user)
                            .number(pn)
                            .normalizedNumber(pn)
                            .name(null)
                            .build()));
            convo.getParticipants().add(c);
        }
        return conversationRepository.save(convo);
    }
}
