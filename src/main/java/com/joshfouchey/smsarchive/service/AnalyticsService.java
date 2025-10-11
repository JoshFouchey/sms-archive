package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.*;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final ContactRepository contactRepository;
    private final MessageRepository messageRepository;
    private final MessagePartRepository messagePartRepository;

    public AnalyticsService(ContactRepository contactRepository,
                            MessageRepository messageRepository,
                            MessagePartRepository messagePartRepository) {
        this.contactRepository = contactRepository;
        this.messageRepository = messageRepository;
        this.messagePartRepository = messagePartRepository;
    }

    public AnalyticsSummaryDto getSummary() {
        long totalContacts = contactRepository.count();
        long totalMessages = messageRepository.count();
        long totalImages = messagePartRepository.countImageParts();
        return new AnalyticsSummaryDto(totalContacts, totalMessages, totalImages);
    }

    public List<TopContactDto> getTopContacts(int days, int limit) {
        // Always compute all-time top contacts ignoring days parameter
        List<TopContactDto> all = messageRepository.findTopContactsSince(Instant.EPOCH);
        return all.stream().limit(limit).collect(Collectors.toList());
    }

    public List<MessageCountPerDayDto> getMessagesPerDay(int days) {
        // Always compute from the beginning of time (Instant.EPOCH)
        return messageRepository.countMessagesPerDaySince(Instant.EPOCH).stream()
                .map(p -> new MessageCountPerDayDto(
                        p.getDay_ts().toLocalDateTime().toLocalDate(),
                        p.getCount()
                ))
                .collect(Collectors.toList());
    }

    public long getTotalContacts() {
        return contactRepository.count();
    }

    @Cacheable(value = "analyticsDashboard", key = "'ALL|' + #topLimit")
    public AnalyticsDashboardDto getDashboard(int topContactDays, int topLimit, int perDayDays) {
        AnalyticsSummaryDto summary = getSummary();
        List<TopContactDto> top = getTopContacts(0, topLimit);
        List<MessageCountPerDayDto> perDay = getMessagesPerDay(0);
        return new AnalyticsDashboardDto(summary, top, perDay);
    }
}