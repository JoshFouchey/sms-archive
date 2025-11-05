package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.ConversationRepository;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@WithMockUser(username = "convuser")
class ConversationServiceTest extends EnhancedPostgresTestContainer {

    @Autowired private ConversationService conversationService;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        conversationRepository.deleteAll();
        contactRepository.deleteAll();
        userRepository.deleteAll();

        conversationRepository.flush();
        contactRepository.flush();
        userRepository.flush();

        user = userRepository.findByUsername("convuser").orElseGet(() -> {
            User u = new User();
            u.setUsername("convuser");
            u.setPasswordHash("$2a$10$dummyhash");
            return userRepository.save(u);
        });
    }

    @Test
    void createsConversationWhenAbsent() {
        Conversation c = conversationService.findOrCreateOneToOne("15553334444", "Charlie");
        assertThat(c.getId()).isNotNull();
        assertThat(c.getParticipants()).hasSize(1);
        assertThat(c.getParticipants().iterator().next().getNormalizedNumber()).isEqualTo("15553334444");
        assertThat(c.getName()).isEqualTo("Charlie");
    }

    @Test
    void findsExistingConversationOnSecondCall() {
        Conversation first = conversationService.findOrCreateOneToOne("15554445555", "Dana");
        Long firstId = first.getId();
        Conversation second = conversationService.findOrCreateOneToOne("15554445555", "Dana Updated");
        assertThat(second.getId()).isEqualTo(firstId);
        // Name should remain initial (since we didn't implement rename logic here)
        assertThat(second.getName()).isEqualTo(first.getName());
    }

    @Test
    void emptyDisplayNameSetsNullNameForContact() {
        Conversation c = conversationService.findOrCreateOneToOne("15556667777", "");
        assertThat(c.getParticipants().iterator().next().getName()).isNull();
        // Conversation name falls back to number
        assertThat(c.getName()).isEqualTo("15556667777");
    }

    @Test
    void deleteConversationRemovesMessagesAndParts() {
        // Create one-to-one conversation
        Conversation c = conversationService.findOrCreateOneToOne("15557778888", "Eve");
        Long convId = c.getId();
        // Simulate two messages with parts
        com.joshfouchey.smsarchive.model.Contact contact = c.getParticipants().iterator().next();
        com.joshfouchey.smsarchive.model.Message m1 = new com.joshfouchey.smsarchive.model.Message();
        m1.setUser(user);
        m1.setContact(contact);
        m1.setConversation(c);
        m1.setTimestamp(Instant.now());
        m1.setProtocol(com.joshfouchey.smsarchive.model.MessageProtocol.SMS);
        m1.setDirection(com.joshfouchey.smsarchive.model.MessageDirection.INBOUND);
        m1.setBody("Hello");
        com.joshfouchey.smsarchive.model.MessagePart p1 = new com.joshfouchey.smsarchive.model.MessagePart();
        p1.setMessage(m1);
        p1.setSeq(0);
        p1.setContentType("text/plain");
        p1.setText("Hello");
        m1.getParts().add(p1);
        com.joshfouchey.smsarchive.model.Message m2 = new com.joshfouchey.smsarchive.model.Message();
        m2.setUser(user);
        m2.setContact(contact);
        m2.setConversation(c);
        m2.setTimestamp(Instant.now());
        m2.setProtocol(com.joshfouchey.smsarchive.model.MessageProtocol.MMS);
        m2.setDirection(com.joshfouchey.smsarchive.model.MessageDirection.OUTBOUND);
        m2.setBody("Photo");
        com.joshfouchey.smsarchive.model.MessagePart p2 = new com.joshfouchey.smsarchive.model.MessagePart();
        p2.setMessage(m2);
        p2.setSeq(0);
        p2.setContentType("image/jpeg");
        p2.setFilePath("/tmp/test.jpg");
        m2.getParts().add(p2);
        // Persist messages via repository
        conversationRepository.save(c); // ensure conversation managed
        conversationRepository.flush();
        // Save messages
        var messageRepoBean = org.springframework.test.util.ReflectionTestUtils.getField(conversationService, "messageRepository");
        assertThat(messageRepoBean).isNotNull();
        ((com.joshfouchey.smsarchive.repository.MessageRepository) messageRepoBean).save(m1);
        ((com.joshfouchey.smsarchive.repository.MessageRepository) messageRepoBean).save(m2);
        ((com.joshfouchey.smsarchive.repository.MessageRepository) messageRepoBean).flush();
        assertThat(((com.joshfouchey.smsarchive.repository.MessageRepository) messageRepoBean).findAll()).hasSize(2);
        // Delete conversation
        conversationService.deleteConversationById(convId);
        // Verify deletion
        assertThat(conversationRepository.findById(convId)).isEmpty();
        assertThat(((com.joshfouchey.smsarchive.repository.MessageRepository) messageRepoBean).findAll()).isEmpty();
    }
}
