package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.ConversationSummaryDto;
import com.joshfouchey.smsarchive.dto.ConversationTimelineDto;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
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

    /**
     * Load ALL messages for a conversation (cached in Caffeine).
     * Used for client-side search/filter operations.
     */
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "conversationMessages", key = "#conversationId")
    public List<MessageDto> getAllConversationMessages(Long conversationId) {
        var user = currentUserProvider.getCurrentUser();

        // Verify conversation belongs to user
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        // Load all messages sorted by timestamp ascending (oldest first)
        List<Message> messages = messageRepository.findAllByConversationIdAndUser(conversationId, user);

        // Sort by timestamp ascending
        messages.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        return messages.stream()
                .map(MessageMapper::toDto)
                .toList();
    }

    /**
     * Get message count for a conversation (cached).
     */
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "conversationMessageCount", key = "#conversationId")
    public Long getConversationMessageCount(Long conversationId) {
        var user = currentUserProvider.getCurrentUser();

        // Verify conversation belongs to user
        conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        return messageRepository.countByConversationIdAndUser(conversationId, user);
    }

    @Transactional(readOnly = true)
    public ConversationTimelineDto getConversationTimeline(Long conversationId) {
        var user = currentUserProvider.getCurrentUser();

        // Verify conversation belongs to user
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        List<MessageRepository.TimelineBucketProjection> buckets =
                messageRepository.getConversationTimeline(conversationId, user.getId());

        // Group by year
        Map<Integer, List<MessageRepository.TimelineBucketProjection>> byYear = buckets.stream()
                .collect(Collectors.groupingBy(MessageRepository.TimelineBucketProjection::getYear));

        List<ConversationTimelineDto.YearBucket> years = byYear.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(yearEntry -> {
                    int year = yearEntry.getKey();
                    List<MessageRepository.TimelineBucketProjection> monthBuckets = yearEntry.getValue();

                    long yearCount = monthBuckets.stream().mapToLong(MessageRepository.TimelineBucketProjection::getCount).sum();

                    List<ConversationTimelineDto.MonthBucket> months = monthBuckets.stream()
                            .sorted(Comparator.comparingInt(MessageRepository.TimelineBucketProjection::getMonth))
                            .map(mb -> new ConversationTimelineDto.MonthBucket(
                                    year,
                                    mb.getMonth(),
                                    mb.getCount(),
                                    mb.getFirst_message_id(),
                                    mb.getLast_message_id()
                            ))
                            .toList();

                    return new ConversationTimelineDto.YearBucket(year, yearCount, months);
                })
                .toList();

        return new ConversationTimelineDto(conversationId, years);
    }

    @Transactional(readOnly = true)
    public PagedResponse<MessageDto> getConversationMessagesByDateRange(Long conversationId,
                                                                        String dateFromStr,
                                                                        String dateToStr,
                                                                        int page,
                                                                        int size,
                                                                        String sortDir) {
        if (size > 500) size = 500;
        if (size < 1) size = 1;

        var user = currentUserProvider.getCurrentUser();

        // Verify conversation belongs to user
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        Instant dateFrom = parseDate(dateFromStr);
        Instant dateTo = parseDate(dateToStr);

        // If dateTo is null, set it to far future to get all messages after dateFrom
        if (dateTo == null) {
            dateTo = Instant.parse("2099-12-31T23:59:59Z");
        }

        Sort sort = Sort.by("timestamp");
        sort = "asc".equalsIgnoreCase(sortDir) ? sort.ascending() : sort.descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Message> result = messageRepository.findByConversationAndDateRange(
                conversationId, dateFrom, dateTo, user, pageable);

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

    private Instant parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            // Try ISO instant first
            return Instant.parse(dateStr);
        } catch (Exception e) {
            // Try local date (assumes UTC)
            try {
                LocalDate localDate = LocalDate.parse(dateStr);
                return localDate.atStartOfDay(ZoneId.of("UTC")).toInstant();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid date format: " + dateStr);
            }
        }
    }

    /**
     * Helper to construct month boundaries in UTC for a given year/month
     */
    private Instant getMonthStart(int year, int month) {
        return LocalDate.of(year, month, 1)
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant();
    }

    private Instant getMonthEnd(int year, int month) {
        LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
        LocalDate firstDayOfNextMonth = firstDayOfMonth.plusMonths(1);
        return firstDayOfNextMonth.atStartOfDay(ZoneId.of("UTC")).toInstant();
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
    public ConversationSummaryDto renameConversation(Long conversationId, String newName) {
        var user = currentUserProvider.getCurrentUser();
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        
        conversation.setName(newName);
        conversationRepository.save(conversation);
        
        return toSummaryDto(conversation);
    }

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
