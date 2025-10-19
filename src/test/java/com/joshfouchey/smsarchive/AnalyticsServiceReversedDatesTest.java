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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
class AnalyticsServiceReversedDatesTest extends EnhancedPostgresTestContainer {

    @Autowired AnalyticsService analyticsService;
    @Autowired ContactRepository contactRepository;
    @Autowired MessageRepository messageRepository;

    Contact contact;

    @BeforeEach
    void init() {
        messageRepository.deleteAll();
        contactRepository.deleteAll();
        contact = contactRepository.save(Contact.builder().number("+15550123").normalizedNumber("15550123").name("X").build());
        // messages across a 5 day span
        LocalDate start = LocalDate.now().minusDays(5);
        for (int i = 0; i < 5; i++) {
            Message m = new Message();
            m.setContact(contact);
            m.setProtocol(MessageProtocol.SMS);
            m.setDirection(MessageDirection.OUTBOUND);
            m.setTimestamp(start.plusDays(i).atTime(9,0).atZone(ZoneId.systemDefault()).toInstant());
            messageRepository.save(m);
        }
    }

    @Test
    @DisplayName("Reversed date arguments produce same result as ordered")
    void reversedDatesEquivalent() {
        LocalDate a = LocalDate.now().minusDays(5);
        LocalDate b = LocalDate.now().minusDays(1);
        List<MessageCountPerDayDto> ordered = analyticsService.getMessagesPerDayRange(a, b, contact.getId());
        List<MessageCountPerDayDto> reversed = analyticsService.getMessagesPerDayRange(b, a, contact.getId());
        assertEquals(ordered, reversed, "Expected identical aggregation for reversed date inputs");
        assertEquals(5, ordered.size());
    }
}
