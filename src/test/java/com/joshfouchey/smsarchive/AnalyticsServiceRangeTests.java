package com.joshfouchey.smsarchive;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.dto.MessageCountPerDayDto;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessageDirection;
import com.joshfouchey.smsarchive.model.MessageProtocol;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
class AnalyticsServiceRangeTests extends EnhancedPostgresTestContainer {

    @Autowired
    AnalyticsService analyticsService;
    @Autowired
    ContactRepository contactRepository;
    @Autowired
    MessageRepository messageRepository;

    Contact contactA;
    Contact contactB;

    @BeforeEach
    void setup() {
        messageRepository.deleteAll();
        contactRepository.deleteAll();
        contactA = contactRepository.save(Contact.builder().number("+15550001").normalizedNumber("15550001").name("Alice").build());
        contactB = contactRepository.save(Contact.builder().number("+15550002").normalizedNumber("15550002").name("Bob").build());

        // Create messages for contactA on two non-consecutive days
        LocalDate base = LocalDate.now().minusDays(10); // anchor
        persistMessage(contactA, base);                 // Day 0
        persistMessage(contactA, base.plusDays(2));     // Day 2
        persistMessage(contactA, base.plusDays(2));     // Additional same day
        // ContactB messages only on one day
        persistMessage(contactB, base.plusDays(1));
    }

    private void persistMessage(Contact c, LocalDate day) {
        Message m = new Message();
        m.setContact(c);
        m.setProtocol(MessageProtocol.SMS);
        m.setDirection(MessageDirection.INBOUND);
        m.setTimestamp(day.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant());
        m.setBody("Test " + day);
        messageRepository.save(m);
    }

    @Test
    @DisplayName("Single-day range with no messages returns empty list")
    void singleDayNoMessages() {
        LocalDate day = LocalDate.now().minusDays(40); // far before any messages
        List<MessageCountPerDayDto> result = analyticsService.getMessagesPerDayRange(day, day, null);
        assertTrue(result.isEmpty(), "Expected empty list for day with no messages");
    }

    @Test
    @DisplayName("Single-day range with messages returns exactly one entry")
    void singleDayWithMessages() {
        LocalDate target = LocalDate.now().minusDays(10); // base day with one message
        List<MessageCountPerDayDto> result = analyticsService.getMessagesPerDayRange(target, target, null);
        assertEquals(1, result.size(), "Should have exactly one day entry");
        assertEquals(target, result.get(0).day());
        assertEquals(1, result.get(0).count(), "Count should match messages on that day");
    }

    @Test
    @DisplayName("Contact filter includes only existing message days for that contact")
    void contactFilterSparseDays() {
        LocalDate start = LocalDate.now().minusDays(10);
        LocalDate end = start.plusDays(4); // covers days 0..4
        List<MessageCountPerDayDto> allContacts = analyticsService.getMessagesPerDayRange(start, end, null);
        List<MessageCountPerDayDto> contactAOnly = analyticsService.getMessagesPerDayRange(start, end, contactA.getId());
        // contactA messages exist only on day 0 and day 2
        Set<LocalDate> expectedDays = Set.of(start, start.plusDays(2));
        assertEquals(expectedDays, contactAOnly.stream().map(MessageCountPerDayDto::day).collect(Collectors.toSet()));
        // Ensure counts aggregated properly (day 2 has two messages)
        long countDay0 = contactAOnly.stream().filter(d -> d.day().equals(start)).findFirst().get().count();
        long countDay2 = contactAOnly.stream().filter(d -> d.day().equals(start.plusDays(2))).findFirst().get().count();
        assertEquals(1, countDay0);
        assertEquals(2, countDay2);
        // allContacts should include days for both contacts (0,1,2)
        Set<LocalDate> allDays = allContacts.stream().map(MessageCountPerDayDto::day).collect(Collectors.toSet());
        assertEquals(Set.of(start, start.plusDays(1), start.plusDays(2)), allDays);
    }

    @Test
    @DisplayName("Contact with no messages in range returns empty list")
    void contactWithNoMessagesInRange() {
        LocalDate start = LocalDate.now().minusDays(3);
        LocalDate end = start; // single day, no messages for contactA in this late window
        List<MessageCountPerDayDto> result = analyticsService.getMessagesPerDayRange(start, end, contactA.getId());
        assertTrue(result.isEmpty());
    }
}
