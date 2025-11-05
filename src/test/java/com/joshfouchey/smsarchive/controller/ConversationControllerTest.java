// filepath: /Users/jfouchey/development/git/sms-archive/src/test/java/com/joshfouchey/smsarchive/controller/ConversationControllerTest.java
package com.joshfouchey.smsarchive.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.ConversationRepository;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import com.joshfouchey.smsarchive.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "apiuser")
@ActiveProfiles("test")
class ConversationControllerTest extends com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer {

    @Autowired MockMvc mockMvc;
    @Autowired ConversationService conversationService;
    @Autowired ConversationRepository conversationRepository;
    @Autowired ContactRepository contactRepository;
    @Autowired UserRepository userRepository;
    @Autowired ObjectMapper objectMapper;

    private User user;

    @BeforeEach
    void setup() {
        // Clean tables (order matters)
        conversationRepository.deleteAll();
        contactRepository.deleteAll();
        userRepository.deleteAll();
        // Ensure authenticated principal exists in DB
        user = userRepository.findByUsername("apiuser").orElseGet(() -> {
            User u = new User();
            u.setUsername("apiuser");
            u.setPasswordHash("$2a$10$dummyhash");
            return userRepository.save(u);
        });
    }

    @Test
    void deleteConversationReturns204() throws Exception {
        Conversation conv = conversationService.findOrCreateOneToOne("15550123456", "Test Person");
        Long id = conv.getId();
        assertThat(id).isNotNull();
        mockMvc.perform(delete("/api/conversations/" + id)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
        assertThat(conversationRepository.findById(id)).isEmpty();
    }

    @Test
    void deleteConversationReturns404ForMissing() throws Exception {
        mockMvc.perform(delete("/api/conversations/999999")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}

