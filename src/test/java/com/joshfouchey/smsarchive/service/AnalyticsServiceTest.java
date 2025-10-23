package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.MessageCountPerDayDto;
import com.joshfouchey.smsarchive.dto.TopContactDto;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    ContactRepository contactRepository;
    MessageRepository messageRepository;
    MessagePartRepository messagePartRepository;
    AnalyticsService analyticsService;
    CurrentUserProvider currentUserProvider;
    User testUser;

    @BeforeEach
    void setUp() {
        contactRepository = mock(ContactRepository.class);
        messageRepository = mock(MessageRepository.class);
        messagePartRepository = mock(MessagePartRepository.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        when(currentUserProvider.getCurrentUser()).thenReturn(testUser);

        analyticsService = new AnalyticsService(contactRepository, messageRepository, messagePartRepository, currentUserProvider);
    }

    @Test
    void topContactsAlwaysAllTimeIgnoresDays() {
        when(messageRepository.findTopContactsSince(Instant.EPOCH, testUser))
                .thenReturn(List.of(new TopContactDto(1L, "Alice", 7L)));

        List<TopContactDto> result = analyticsService.getTopContacts(30, 10);
        assertThat(result).hasSize(1);
        verify(messageRepository, times(1)).findTopContactsSince(Instant.EPOCH, testUser);

        // Calling with different days still only uses all-time
        analyticsService.getTopContacts(5, 10);
        verify(messageRepository, times(2)).findTopContactsSince(Instant.EPOCH, testUser);
    }

    @Test
    void messagesPerDayAlwaysAllTime() {
        MessageRepository.DayCountProjection projection = new MessageRepository.DayCountProjection() {
            @Override public java.sql.Timestamp getDay_ts() { return java.sql.Timestamp.from(Instant.now()); }
            @Override public long getCount() { return 3L; }
        };
        when(messageRepository.countMessagesPerDaySince(Instant.EPOCH, testUser.getId()))
                .thenReturn(List.of(projection));

        List<MessageCountPerDayDto> list = analyticsService.getMessagesPerDay(90);
        assertThat(list).hasSize(1);
        verify(messageRepository, times(1)).countMessagesPerDaySince(Instant.EPOCH, testUser.getId());
    }
}
