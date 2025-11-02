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
}

