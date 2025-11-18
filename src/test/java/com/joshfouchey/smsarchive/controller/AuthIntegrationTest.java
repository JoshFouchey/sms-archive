package com.joshfouchey.smsarchive.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest extends EnhancedPostgresTestContainer {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    ContactRepository contactRepository;
    @Autowired
    MessageRepository messageRepository;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void cleanup() {
        messageRepository.deleteAll();
        contactRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registrationAndDuplicate() throws Exception {
        Map<String,String> req = Map.of("username","testuser","password","password123");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginAndAccessProtected() throws Exception {
        Map<String,String> reg = Map.of("username","loginuser","password","password123");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(reg)))
                .andExpect(status().isOk());
        Map<String,String> login = Map.of("username","loginuser","password","password123");
        String token = mapper.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(login)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(get("/api/messages/contacts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/messages/contacts").header("Authorization","Bearer "+token))
                .andExpect(status().isOk());
    }

    @Test
    void caseInsensitiveUsername() throws Exception {
        // Register with lowercase
        Map<String,String> reg = Map.of("username","caseuser","password","password123");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        // Try to register with different case - should fail as duplicate
        Map<String,String> regUpper = Map.of("username","CaseUser","password","password123");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(regUpper)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username already taken"));

        // Login with mixed case - should succeed
        Map<String,String> loginMixed = Map.of("username","CaseUser","password","password123");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(loginMixed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        // Login with uppercase - should succeed
        Map<String,String> loginUpper = Map.of("username","CASEUSER","password","password123");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(loginUpper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        // Verify username is stored in lowercase
        var user = userRepository.findByUsername("caseuser");
        assert user.isPresent();
        assert user.get().getUsername().equals("caseuser");
    }
}
