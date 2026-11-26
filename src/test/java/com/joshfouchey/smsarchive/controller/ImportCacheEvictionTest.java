package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.repository.ConversationRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caches are cleared when an import finishes (success or failure), so data read before or during
 * the import is not served stale afterwards. Imports run inline in the test profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.cache.type=caffeine")
class ImportCacheEvictionTest extends EnhancedPostgresTestContainer {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CacheManager cacheManager;
    @Autowired UserRepository userRepo;
    @Autowired ConversationRepository conversationRepo;

    String username;
    String token;

    @BeforeEach
    void setup() throws Exception {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        username = "evict" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", "password123"))))
                .andReturn().getResponse().getContentAsString();
        token = mapper.readTree(body).get("accessToken").asString();
    }

    private static byte[] sms(long date, String body) {
        return ("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><smses count=\"1\">"
                + "<sms protocol=\"0\" address=\"15553334444\" date=\"" + date + "\" type=\"1\" body=\"" + body
                + "\" contact_name=\"Evict Test\" /></smses>").getBytes(StandardCharsets.UTF_8);
    }

    private void upload(byte[] content, String expectedStatus) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "export.xml", "text/xml", content);
        String body = mvc.perform(multipart("/import/stream").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jobId = mapper.readTree(body).get("jobId").asString();
        mvc.perform(get("/import/progress/" + jobId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    @Test
    void conversationCachesAreFreshAfterImportFinishes() throws Exception {
        upload(sms(1672531200000L, "first message"), "COMPLETED");
        Long conversationId = conversationRepo
                .findAllByUserOrderByLastMessage(userRepo.findByUsername(username).orElseThrow())
                .getFirst().getId();
        String base = "/api/conversations/" + conversationId;

        // Warm the per-conversation caches with one message
        mvc.perform(get(base + "/messages/all").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(base + "/messages/count").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.count").value(1));

        upload(sms(1672617600000L, "second message"), "COMPLETED");

        mvc.perform(get(base + "/messages/all").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(base + "/messages/count").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void failedImportStillClearsCaches() throws Exception {
        for (String name : cacheManager.getCacheNames()) {
            cacheManager.getCache(name).put("stale", "value");
        }

        upload("<smses><sms address=".getBytes(StandardCharsets.UTF_8), "FAILED");

        for (String name : java.util.List.of("analyticsDashboard", "contactSummaries", "conversationList",
                "distinctContacts", "conversationTimeline", "conversationMessages",
                "conversationMessageCount", "messageContext")) {
            assertThat(cacheManager.getCache(name).get("stale")).as(name).isNull();
        }
        assertThat(cacheManager.getCache("currentUser").get("stale")).isNotNull();
    }
}
