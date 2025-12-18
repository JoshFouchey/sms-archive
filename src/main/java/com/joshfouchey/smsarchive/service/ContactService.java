package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.ContactDto;
import com.joshfouchey.smsarchive.dto.ContactMergeResultDto;
import com.joshfouchey.smsarchive.mapper.ContactMapper;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.ConversationRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final CurrentUserProvider currentUserProvider;

    public ContactService(ContactRepository contactRepository,
                         MessageRepository messageRepository,
                         ConversationRepository conversationRepository,
                         CurrentUserProvider currentUserProvider) {
        this.contactRepository = contactRepository;
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<ContactDto> getAllDistinctContacts() {
        var user = currentUserProvider.getCurrentUser();
        return contactRepository.findAllByUser(user).stream()
                .sorted(Comparator
                        .comparing((com.joshfouchey.smsarchive.model.Contact c) -> c.getName() == null) // non-null first
                        .thenComparing(c -> c.getName() == null ? "" : c.getName().toLowerCase())
                        .thenComparing(com.joshfouchey.smsarchive.model.Contact::getNormalizedNumber))
                .map(ContactMapper::toDto)
                .toList();
    }

    @Transactional
    public ContactDto updateContactName(Long contactId, String name) {
        var user = currentUserProvider.getCurrentUser();
        var contact = contactRepository.findById(contactId).orElseThrow(java.util.NoSuchElementException::new);
        if (!contact.getUser().equals(user)) {
            // Do not expose existence; treat as not found
            throw new java.util.NoSuchElementException();
        }
        // Normalize name: trim; blank -> null
        String normalized = name == null ? null : name.trim();
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }
        contact.setName(normalized);
        // JPA will flush on transaction commit; return updated DTO
        return com.joshfouchey.smsarchive.mapper.ContactMapper.toDto(contact);
    }

    @Transactional
    public ContactMergeResultDto mergeContacts(Long primaryContactId, Long mergeFromContactId) {
        var user = currentUserProvider.getCurrentUser();

        // Validate both contacts exist and belong to user
        Contact primaryContact = contactRepository.findById(primaryContactId)
                .orElseThrow(() -> new RuntimeException("Primary contact not found"));
        Contact mergeFromContact = contactRepository.findById(mergeFromContactId)
                .orElseThrow(() -> new RuntimeException("Contact to merge not found"));

        if (!primaryContact.getUser().equals(user) || !mergeFromContact.getUser().equals(user)) {
            throw new RuntimeException("Contacts do not belong to current user");
        }

        if (primaryContactId.equals(mergeFromContactId)) {
            throw new RuntimeException("Cannot merge contact with itself");
        }

        // Find all conversations involving the old contact
        List<Conversation> oldConversations = conversationRepository.findByParticipant(mergeFromContact);

        int messagesTransferred = 0;
        int duplicatesSkipped = 0;
        Set<Long> processedConversations = new HashSet<>();

        // Process each conversation
        for (Conversation oldConv : oldConversations) {
            // Check if primary contact already has a conversation with the same participants
            // For now, we'll merge messages into the primary contact's conversations
            List<Message> messages = messageRepository.findAllByConversationIdAndUser(oldConv.getId(), user);

            for (Message message : messages) {
                try {
                    // Update sender contact if it's the old contact
                    if (message.getSenderContact() != null &&
                        message.getSenderContact().getId().equals(mergeFromContactId)) {
                        message.setSenderContact(primaryContact);
                    }
                    messageRepository.save(message);
                    messagesTransferred++;
                } catch (DataIntegrityViolationException e) {
                    // Duplicate message detected by unique constraint
                    duplicatesSkipped++;
                }
            }

            // Replace old contact with primary contact in conversation participants
            if (oldConv.getParticipants().remove(mergeFromContact)) {
                oldConv.getParticipants().add(primaryContact);
                conversationRepository.save(oldConv);
                processedConversations.add(oldConv.getId());
            }
        }

        // Mark the old contact as merged and delete it
        // We keep track of the merge for a moment before deletion
        mergeFromContact.setMergedInto(primaryContact);
        mergeFromContact.setMergedAt(Instant.now());
        contactRepository.save(mergeFromContact);
        
        // Now delete the merged contact (all references have been updated)
        contactRepository.delete(mergeFromContact);

        String primaryName = primaryContact.getName() != null ? primaryContact.getName() : primaryContact.getNumber();
        String mergedName = mergeFromContact.getName() != null ? mergeFromContact.getName() : mergeFromContact.getNumber();

        return new ContactMergeResultDto(
                primaryContactId,
                primaryName,
                mergeFromContactId,
                mergedName,
                messagesTransferred,
                duplicatesSkipped,
                processedConversations.size(),
                true,
                String.format("Successfully merged %s into %s. Transferred %d messages, skipped %d duplicates.",
                        mergedName, primaryName, messagesTransferred, duplicatesSkipped)
        );
    }
}
