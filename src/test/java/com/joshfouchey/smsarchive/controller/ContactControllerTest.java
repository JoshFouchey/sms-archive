package com.joshfouchey.smsarchive.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import com.joshfouchey.smsarchive.service.ContactService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "apiuser")
@ActiveProfiles("test")
class ContactControllerTest extends com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer {

    @Autowired MockMvc mockMvc;
    @Autowired ContactService contactService;
    @Autowired ContactRepository contactRepository;
    @Autowired UserRepository userRepository;
    @Autowired ObjectMapper objectMapper;

    private User user;
    private Contact contact;

    @BeforeEach
    void setup() {
        contactRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.findByUsername("apiuser").orElseGet(() -> {
            User u = new User();
            u.setUsername("apiuser");
            u.setPasswordHash("$2a$10$dummyhash");
            return userRepository.save(u);
        });
        // create a contact owned by user
        contact = Contact.builder()
                .user(user)
                .number("+1 (555) 012-3456")
                .normalizedNumber("15550123456")
                .name(null)
                .build();
        contact = contactRepository.save(contact);
    }

    @Test
    void updateContactName_setsName_andReturns200() throws Exception {
        Long id = contact.getId();
        String payload = "{\"name\":\"Alice Smith\"}";
        mockMvc.perform(put("/api/contacts/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());
        var updated = contactRepository.findById(id).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Alice Smith");
    }

    @Test
    void updateContactName_blankClearsName() throws Exception {
        // first set a name
        contact.setName("Temp");
        contactRepository.save(contact);
        Long id = contact.getId();
        String payload = "{\"name\":\"   \"}"; // blanks -> null
        mockMvc.perform(put("/api/contacts/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());
        var updated = contactRepository.findById(id).orElseThrow();
        assertThat(updated.getName()).isNull();
    }

    @Test
    void updateContactName_notFoundForOtherUserContact() throws Exception {
        // simulate different owner by creating another user & reassign contact (direct DB change)
        User other = new User();
        other.setUsername("otheruser");
        other.setPasswordHash("$2a$10$dummyhash");
        other = userRepository.save(other);
        contact.setUser(other);
        contactRepository.save(contact);
        String payload = "{\"name\":\"Bob\"}";
        mockMvc.perform(put("/api/contacts/" + contact.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateContactName_notFoundForMissingId() throws Exception {
        String payload = "{\"name\":\"Alice\"}";
        mockMvc.perform(put("/api/contacts/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isNotFound());
    }
}

