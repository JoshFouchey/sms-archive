package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.model.*;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Merged test suite for MediaService covering:
 *  - No filter returns all image parts
 *  - Filtering by contact1 and contact2
 *  - Pagination across multiple images
 *  - Invalid contactId (404 behavior via exception)
 */
@SpringBootTest
@WithMockUser(username = "testuser")
class MediaServiceTest extends EnhancedPostgresTestContainer {

    @Autowired private MediaService mediaService;
    @Autowired private ContactRepository contactRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private MessagePartRepository messagePartRepository;
    @Autowired private UserRepository userRepository;

    private Long contact1Id;
    private Long contact2Id;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Clean in dependency order
        messagePartRepository.deleteAll();
        messageRepository.deleteAll();
        contactRepository.deleteAll();
        userRepository.deleteAll();

        // Flush to ensure deletes are committed before inserts
        messagePartRepository.flush();
        messageRepository.flush();
        contactRepository.flush();
        userRepository.flush();

        // Create test user (or reuse if exists due to container reuse)
        testUser = userRepository.findByUsername("testuser").orElseGet(() -> {
            User user = new User();
            user.setUsername("testuser");
            user.setPasswordHash("$2a$10$dummyhash");
            return userRepository.save(user);
        });

        Contact c1 = Contact.builder().number("+1 (555) 111-1111").normalizedNumber("15551111111").name("Alice").user(testUser).build();
        Contact c2 = Contact.builder().number("+1 (555) 222-2222").normalizedNumber("15552222222").name("Bob").user(testUser).build();
        c1 = contactRepository.save(c1);
        c2 = contactRepository.save(c2);
        contact1Id = c1.getId();
        contact2Id = c2.getId();

        Message m1 = baseMessage(c1, "15551111111", "me", Instant.now().minusSeconds(300), MessageProtocol.SMS, MessageDirection.INBOUND, "Hello from Alice 1");
        Message m2 = baseMessage(c1, "me", "15551111111", Instant.now().minusSeconds(200), MessageProtocol.SMS, MessageDirection.OUTBOUND, "Reply to Alice");
        Message m3 = baseMessage(c2, "15552222222", "me", Instant.now().minusSeconds(100), MessageProtocol.MMS, MessageDirection.INBOUND, "Bob sent an image");

        m1 = messageRepository.save(m1);
        m2 = messageRepository.save(m2);
        m3 = messageRepository.save(m3);

        addImagePart(m1, "media/a1.jpg", "image/jpeg");
        addImagePart(m2, "media/a2.png", "image/png");
        addImagePart(m3, "media/b1.jpg", "image/jpeg");
        addNonImagePart(m3, "media/b1.txt", "text/plain"); // excluded by LIKE 'image/%'
    }

    private Message baseMessage(Contact contact, String sender, String recipient, Instant ts, MessageProtocol protocol, MessageDirection dir, String body) {
        Message m = new Message();
        m.setProtocol(protocol);
        m.setDirection(dir);
        m.setSender(sender);
        m.setRecipient(recipient);
        m.setContact(contact);
        m.setUser(testUser);
        m.setTimestamp(ts);
        m.setBody(body);
        return m;
    }

    private void addImagePart(Message message, String path, String contentType) {
        MessagePart part = new MessagePart();
        part.setMessage(message);
        part.setContentType(contentType);
        part.setFilePath(path);
        part.setName(path.substring(path.lastIndexOf('/') + 1));
        part.setSizeBytes(120L);
        messagePartRepository.save(part);
    }

    private void addNonImagePart(Message message, String path, String contentType) {
        MessagePart part = new MessagePart();
        part.setMessage(message);
        part.setContentType(contentType);
        part.setFilePath(path);
        part.setName(path.substring(path.lastIndexOf('/') + 1));
        part.setSizeBytes(50L);
        messagePartRepository.save(part);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void noFilter_returnsAllImageParts() {
        var page = mediaService.getImages(null, 0, 50);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).allMatch(p -> p.getContentType().startsWith("image/"));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void filter_contact1_returnsTwoImages() {
        var page = mediaService.getImages(contact1Id, 0, 10);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(p -> p.getMessage().getContact().getId().equals(contact1Id));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void filter_contact2_returnsOneImage() {
        var page = mediaService.getImages(contact2Id, 0, 10);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).allMatch(p -> p.getMessage().getContact().getId().equals(contact2Id));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void pagination_splitsImagesForContact1() {
        var firstPage = mediaService.getImages(contact1Id, 0, 1);
        var secondPage = mediaService.getImages(contact1Id, 1, 1);
        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(firstPage.getContent().getFirst().getId())
            .isNotEqualTo(secondPage.getContent().getFirst().getId());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void invalidContact_throwsEntityNotFound() {
        assertThatThrownBy(() -> mediaService.getImages(999999L, 0, 10))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Contact not found");
    }
}
