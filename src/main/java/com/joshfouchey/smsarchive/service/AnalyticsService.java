package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.*;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final ContactRepository contactRepository;
    private final MessageRepository messageRepository;
    private final MessagePartRepository messagePartRepository;

    @PersistenceContext
    private EntityManager entityManager;

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

    public List<MessageCountPerDayDto> getMessagesPerDayRange(LocalDate startDate, LocalDate endDate, Long contactId) {
        if (startDate.isAfter(endDate)) { LocalDate tmp = startDate; startDate = endDate; endDate = tmp; }
        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endExclusive = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        StringBuilder sql = new StringBuilder("SELECT CAST(m.timestamp AS DATE) AS day_date, COUNT(*) AS count FROM messages m WHERE m.timestamp >= :start AND m.timestamp < :end");
        if (contactId != null) sql.append(" AND m.contact_id = :contactId");
        sql.append(" GROUP BY day_date ORDER BY day_date");
        var query = entityManager.createNativeQuery(sql.toString())
                .setParameter("start", start)
                .setParameter("end", endExclusive);
        if (contactId != null) query.setParameter("contactId", contactId);
        @SuppressWarnings("unchecked") List<Object[]> rows = query.getResultList();
        return rows.stream().map(r -> new MessageCountPerDayDto(((java.sql.Date) r[0]).toLocalDate(), ((Number) r[1]).longValue())).collect(Collectors.toList());
    }

    public AnalyticsDashboardDto getDashboard(int topContactDays, int topLimit, int perDayDays, LocalDate startDate, LocalDate endDate, Long contactId) {
        // Fallback: if no explicit range provided, derive from perDayDays ending today
        LocalDate derivedEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate derivedStart = startDate != null ? startDate : derivedEnd.minusDays(Math.max(perDayDays, 1) - 1);
        AnalyticsSummaryDto summary = getSummary();
        List<TopContactDto> top = getTopContacts(0, topLimit);
        List<MessageCountPerDayDto> perDay = getMessagesPerDayRange(derivedStart, derivedEnd, contactId);
        return new AnalyticsDashboardDto(summary, top, perDay);
    }
}