package com.joshfouchey.smsarchive.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.MessageDirection;
import com.joshfouchey.smsarchive.model.MessageProtocol;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import com.joshfouchey.smsarchive.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserIsolationTest extends EnhancedPostgresTestContainer {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepo;
    @Autowired ContactRepository contactRepo;
    @Autowired MessageRepository messageRepo;

    String tokenA;
    String tokenB;
    User userA;
    User userB;
    Contact contactA;

    @BeforeEach
    void setup() throws Exception {
        messageRepo.deleteAll();
        contactRepo.deleteAll();
        userRepo.deleteAll();
        tokenA = mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username","uA","password","password123")))).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        tokenB = mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username","uB","password","password123")))).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        userA = userRepo.findByUsername("uA").orElseThrow();
        userB = userRepo.findByUsername("uB").orElseThrow();
        contactA = new Contact();
        contactA.setUser(userA);
        contactA.setNumber("+15551234567");
        contactA.setNormalizedNumber("15551234567");
        contactRepo.save(contactA);
        Message m = new Message();
        m.setUser(userA);
        m.setContact(contactA);
        m.setSenderContact(contactA); // INBOUND: sender is the contact
        m.setProtocol(MessageProtocol.SMS);
        m.setDirection(MessageDirection.INBOUND);
        m.setTimestamp(Instant.now());
        m.setBody("Hello A");
        messageRepo.save(m);
    }

    @Test
    void userBCannotSeeUserAMessages() throws Exception {
        assertThat(tokenB).isNotNull().isNotEmpty();
        mvc.perform(get("/api/messages/contacts").header("Authorization","Bearer "+tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        assertThat(messageRepo.findAll()).hasSize(1); // only A's message in DB
    }
}
