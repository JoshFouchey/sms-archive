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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caching is disabled in the test profile, so this class re-enables it to verify that
 * per-conversation caches are keyed per user and a cache hit never bypasses the ownership check.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.cache.type=caffeine")
class ConversationCacheIsolationTest extends EnhancedPostgresTestContainer {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CacheManager cacheManager;
    @Autowired UserRepository userRepo;
    @Autowired ContactRepository contactRepo;
    @Autowired ConversationRepository conversationRepo;
    @Autowired MessageRepository messageRepo;

    String tokenA;
    String tokenB;
    Long conversationId;
    Long messageId;

    @BeforeEach
    void setup() throws Exception {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        messageRepo.deleteAll();
        conversationRepo.deleteAll();
        contactRepo.deleteAll();
        userRepo.deleteAll();

        tokenA = register("cacheA");
        tokenB = register("cacheB");
        User userA = userRepo.findByUsername("cachea").orElseThrow();

        Contact contact = new Contact();
        contact.setUser(userA);
        contact.setNumber("+15557654321");
        contact.setNormalizedNumber("15557654321");
        contactRepo.save(contact);

        Conversation conv = new Conversation();
        conv.setUser(userA);
        conv.setName("A's private conversation");
        conv.getParticipants().add(contact);
        conv = conversationRepo.save(conv);
        conversationId = conv.getId();

        Message m = new Message();
        m.setUser(userA);
        m.setConversation(conv);
        m.setSenderContact(contact);
        m.setProtocol(MessageProtocol.SMS);
        m.setDirection(MessageDirection.INBOUND);
        m.setTimestamp(Instant.now());
        m.setBody("secret from A");
        messageId = messageRepo.save(m).getId();
    }

    private String register(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", "password123"))))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asString();
    }

    @Test
    void cachedConversationDataIsNotServedToAnotherUser() throws Exception {
        String base = "/api/conversations/" + conversationId;

        // User A warms all three per-conversation caches
        mvc.perform(get(base + "/messages/all").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("secret from A"));
        mvc.perform(get(base + "/messages/count").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mvc.perform(get(base + "/timeline").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // User B must not get A's cached results
        mvc.perform(get(base + "/messages/all").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(get(base + "/messages/count").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(get(base + "/timeline").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // A still gets its own data (served from cache)
        mvc.perform(get(base + "/messages/all").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("secret from A"));
    }

    @Test
    void cachedMessageContextIsNotServedToAnotherUser() throws Exception {
        String url = "/api/messages/" + messageId + "/context";

        mvc.perform(get(url).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.center.body").value("secret from A"));

        // B must not get A's cached context
        mvc.perform(get(url).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.center").doesNotExist());
    }

    @Test
    void foreignMissCachedForOneUserDoesNotHideOwnersContext() throws Exception {
        String url = "/api/messages/" + messageId + "/context";

        // B asks first: not found for B (null result is cached)
        mvc.perform(get(url).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.center").doesNotExist());

        // The owner still gets their context
        mvc.perform(get(url).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.center.body").value("secret from A"));
    }
}
