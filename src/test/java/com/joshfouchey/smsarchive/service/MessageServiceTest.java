package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.ContactSummaryDto;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageServiceTest {

    private TimeZone originalDefault;

    @BeforeEach
    void forceNonUtcDefaultZone() {
        originalDefault = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    }

    @AfterEach
    void restoreDefaultZone() {
        TimeZone.setDefault(originalDefault);
    }

    @Test
    void contactSummaryTimestamp_isInterpretedAsUtcRegardlessOfJvmZone() {
        MessageRepository repo = mock(MessageRepository.class);
        CurrentUserProvider users = mock(CurrentUserProvider.class);
        User user = new User();
        user.setId(UUID.randomUUID());
        when(users.getCurrentUser()).thenReturn(user);

        MessageRepository.ContactSummaryProjection row = mock(MessageRepository.ContactSummaryProjection.class);
        when(row.getContactId()).thenReturn(1L);
        when(row.getLastMessageTimestamp()).thenReturn(LocalDateTime.of(2026, 11, 19, 0, 35, 1));
        when(repo.findAllContactSummaries(user.getId())).thenReturn(List.of(row));

        List<ContactSummaryDto> result = new MessageService(repo, users).getAllContactSummaries();

        assertThat(result).singleElement()
                .extracting(ContactSummaryDto::getLastMessageTimestamp)
                .isEqualTo(Instant.parse("2026-11-19T00:35:01Z"));
    }
}
