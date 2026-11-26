package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessageDirection;
import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.model.MessageProtocol;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.ConversationRepository;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MediaOwnershipTest extends EnhancedPostgresTestContainer {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepo;
    @Autowired ContactRepository contactRepo;
    @Autowired ConversationRepository conversationRepo;
    @Autowired MessageRepository messageRepo;
    @Autowired MessagePartRepository partRepo;

    @TempDir Path mediaDir;

    String tokenA;
    String tokenB;
    Long contactId;
    Long messageId;
    Long partId;
    Path imageFile;
    Path thumbFile;

    @BeforeEach
    void setup() throws Exception {
        messageRepo.deleteAll();
        conversationRepo.deleteAll();
        contactRepo.deleteAll();
        userRepo.deleteAll();

        tokenA = register("mediaA");
        tokenB = register("mediaB");
        User userA = userRepo.findByUsername("mediaa").orElseThrow();

        Contact contact = new Contact();
        contact.setUser(userA);
        contact.setNumber("+15553334444");
        contact.setNormalizedNumber("15553334444");
        contactId = contactRepo.save(contact).getId();

        Conversation conv = new Conversation();
        conv.setUser(userA);
        conv.getParticipants().add(contact);
        conv = conversationRepo.save(conv);

        imageFile = Files.writeString(mediaDir.resolve("part0.jpg"), "img");
        thumbFile = Files.writeString(mediaDir.resolve("part0_thumb.jpg"), "thumb");

        Message m = new Message();
        m.setUser(userA);
        m.setConversation(conv);
        m.setSenderContact(contact);
        m.setProtocol(MessageProtocol.MMS);
        m.setDirection(MessageDirection.INBOUND);
        m.setTimestamp(Instant.now());
        m = messageRepo.save(m);
        messageId = m.getId();

        MessagePart part = new MessagePart();
        part.setMessage(m);
        part.setSeq(0);
        part.setContentType("image/jpeg");
        part.setFilePath(imageFile.toString());
        partId = partRepo.save(part).getId();
    }

    private String register(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", "password123"))))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asString();
    }

    @Test
    void otherUserCannotDeleteImage_ownerCan() throws Exception {
        mvc.perform(delete("/api/media/images/" + partId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        assertThat(imageFile).exists();
        assertThat(thumbFile).exists();
        assertThat(messageRepo.existsById(messageId)).isTrue();
        assertThat(partRepo.existsById(partId)).isTrue();

        mvc.perform(delete("/api/media/images/" + partId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        assertThat(imageFile).doesNotExist();
        assertThat(thumbFile).doesNotExist();
        assertThat(messageRepo.existsById(messageId)).isFalse();
    }

    @Test
    void galleryByContact_isScopedToOwner() throws Exception {
        mvc.perform(get("/api/media/images").param("contactId", contactId.toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(partId));

        mvc.perform(get("/api/media/images").param("contactId", contactId.toString())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }
}
