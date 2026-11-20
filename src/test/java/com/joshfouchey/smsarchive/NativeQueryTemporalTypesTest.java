package com.joshfouchey.smsarchive;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.dto.ContactSummaryDto;
import com.joshfouchey.smsarchive.dto.MessageCountPerDayDto;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessageDirection;
import com.joshfouchey.smsarchive.model.MessageProtocol;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.ConversationRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import com.joshfouchey.smsarchive.service.AnalyticsService;
import com.joshfouchey.smsarchive.service.ConversationService;
import com.joshfouchey.smsarchive.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Native queries mapped to interface projections / Object[] rows against real Postgres.
 * Hibernate 7 returns java.time types (not java.sql.*) for native temporal columns;
 * these tests pin that every such read path still works end to end.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "temporaluser")
class NativeQueryTemporalTypesTest extends EnhancedPostgresTestContainer {

    private static final Instant SENT_AT = Instant.parse("2026-03-15T12:34:56Z");
    // messages.timestamp is TIMESTAMP (no zone); Hibernate writes Instants as UTC wall-clock.
    private static final LocalDateTime STORED_WALL_CLOCK = LocalDateTime.ofInstant(SENT_AT, ZoneOffset.UTC);

    @Autowired MessageRepository messageRepository;
    @Autowired ContactRepository contactRepository;
    @Autowired UserRepository userRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired MessageService messageService;
    @Autowired AnalyticsService analyticsService;
    @Autowired ConversationService conversationService;

    private Long conversationId;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        contactRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setUsername("temporaluser");
        user.setPasswordHash("$2a$10$dummyhash");
        user = userRepository.save(user);

        Contact contact = contactRepository.save(Contact.builder()
                .number("+15550001").normalizedNumber("15550001").name("Alice").user(user).build());

        Conversation conv = new Conversation();
        conv.setUser(user);
        conv.setName("Alice");
        conv.getParticipants().add(contact);
        conv = conversationRepository.save(conv);
        conversationId = conv.getId();

        Message m = new Message();
        m.setConversation(conv);
        m.setUser(user);
        m.setSenderContact(contact);
        m.setProtocol(MessageProtocol.SMS);
        m.setDirection(MessageDirection.INBOUND);
        m.setTimestamp(SENT_AT);
        m.setBody("hello");
        messageRepository.save(m);
    }

    @Test
    void contactSummariesRoundTripLastMessageInstant() {
        List<ContactSummaryDto> summaries = messageService.getAllContactSummaries();

        assertThat(summaries).hasSize(1);
        // Read back in the JVM zone (pre-existing behaviour; exact round trip only when TZ=UTC, as deployed).
        assertThat(summaries.get(0).getLastMessageTimestamp())
                .isEqualTo(STORED_WALL_CLOCK.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void messagesPerDayReturnsTheMessageDay() {
        List<MessageCountPerDayDto> perDay = analyticsService.getMessagesPerDay(30);

        assertThat(perDay).extracting(MessageCountPerDayDto::day)
                .containsExactly(STORED_WALL_CLOCK.toLocalDate());
    }

    @Test
    void messagesPerDayRangeReturnsTheMessageDay() {
        LocalDate day = STORED_WALL_CLOCK.toLocalDate();

        List<MessageCountPerDayDto> perDay = analyticsService.getMessagesPerDayRange(day, day, null);

        assertThat(perDay).extracting(MessageCountPerDayDto::day).containsExactly(day);
    }

    @Test
    void conversationTimelineBuildsBuckets() {
        assertThat(conversationService.getConversationTimeline(conversationId)).isNotNull();
        assertThat(messageRepository.getConversationTimeline(conversationId,
                userRepository.findByUsername("temporaluser").orElseThrow().getId()).get(0).getFirst_timestamp())
                .isEqualTo(STORED_WALL_CLOCK);
    }
}
