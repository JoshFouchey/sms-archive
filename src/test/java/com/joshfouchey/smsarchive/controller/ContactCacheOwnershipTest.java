package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caching is disabled in the test profile, so this class re-enables it. With a warm "currentUser"
 * cache the current user is a detached instance from an earlier request; ownership checks must
 * still accept the real owner and reject everyone else.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.cache.type=caffeine")
class ContactCacheOwnershipTest extends EnhancedPostgresTestContainer {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CacheManager cacheManager;
    @Autowired UserRepository userRepo;
    @Autowired ContactRepository contactRepo;
    @Autowired ConversationRepository conversationRepo;
    @Autowired MessageRepository messageRepo;

    String tokenA;
    String tokenB;
    Long primaryId;
    Long mergeFromId;

    @BeforeEach
    void setup() throws Exception {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        messageRepo.deleteAll();
        conversationRepo.deleteAll();
        contactRepo.deleteAll();
        userRepo.deleteAll();

        tokenA = register("ownerA");
        tokenB = register("otherB");
        User userA = userRepo.findByUsername("ownera").orElseThrow();

        primaryId = contact(userA, "15550001111").getId();
        Contact mergeFrom = contact(userA, "15550002222");
        mergeFromId = mergeFrom.getId();

        Conversation conv = new Conversation();
        conv.setUser(userA);
        conv.getParticipants().add(mergeFrom);
        conv = conversationRepo.save(conv);

        Message m = new Message();
        m.setUser(userA);
        m.setConversation(conv);
        m.setSenderContact(mergeFrom);
        m.setProtocol(MessageProtocol.SMS);
        m.setDirection(MessageDirection.INBOUND);
        m.setTimestamp(Instant.now());
        m.setBody("hi");
        messageRepo.save(m);
    }

    private Contact contact(User user, String normalized) {
        Contact c = new Contact();
        c.setUser(user);
        c.setNumber("+" + normalized);
        c.setNormalizedNumber(normalized);
        return contactRepo.save(c);
    }

    private String register(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", "password123"))))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asString();
    }

    private void warmCurrentUserCache(String token) throws Exception {
        mvc.perform(get("/api/contacts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCanRenameWithWarmCache_otherUserGets404() throws Exception {
        warmCurrentUserCache(tokenA);
        mvc.perform(put("/api/contacts/" + primaryId).header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"));

        warmCurrentUserCache(tokenB);
        mvc.perform(put("/api/contacts/" + primaryId).header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hijack\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanMergeWithWarmCache_otherUserGets400NotFound() throws Exception {
        warmCurrentUserCache(tokenB);
        mvc.perform(post("/api/contacts/" + primaryId + "/merge/" + mergeFromId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Primary contact not found"));

        warmCurrentUserCache(tokenA);
        mvc.perform(post("/api/contacts/" + primaryId + "/merge/" + mergeFromId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messagesTransferred").value(1));
    }
}
