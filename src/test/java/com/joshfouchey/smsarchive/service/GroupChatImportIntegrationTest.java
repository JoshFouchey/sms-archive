package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.model.ConversationType;
import com.joshfouchey.smsarchive.repository.ConversationRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "testuser")
public class GroupChatImportIntegrationTest extends EnhancedPostgresTestContainer {

    @Autowired ImportService importService;
    @Autowired MessageRepository messageRepository;
    @Autowired MessagePartRepository messagePartRepository;
    @Autowired ContactRepository contactRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired UserRepository userRepository;

    private com.joshfouchey.smsarchive.model.User testUser;

    @BeforeEach
    void setup() {
        messagePartRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        contactRepository.deleteAll();
        userRepository.deleteAll();
        testUser = new com.joshfouchey.smsarchive.model.User();
        testUser.setUsername("testuser");
        testUser.setPasswordHash("$2a$10$dummyhash");
        testUser = userRepository.save(testUser);
    }

    @Test
    void importsGroupChatAndCreatesConversation() throws Exception {
        Path xml = Path.of("src/main/resources/test_files/sms-20251101215610.xml");
        UUID jobId = importService.startImportAsync(xml);
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            var p = importService.getProgress(jobId);
            return p != null && ("COMPLETED".equals(p.getStatus()) || "FAILED".equals(p.getStatus()));
        });
        var progress = importService.getProgress(jobId);
        Assertions.assertThat(progress.getStatus()).isEqualTo("COMPLETED");

        long convoCount = conversationRepository.findAllByUserOrderByLastMessage(testUser).stream().count();
        Assertions.assertThat(convoCount).isGreaterThanOrEqualTo(1);

        var groupConvos = conversationRepository.findAllByUserOrderByLastMessage(testUser).stream()
                .filter(c -> c.getType() == ConversationType.GROUP)
                .toList();
        Assertions.assertThat(groupConvos).isNotEmpty();

        var anyGroup = groupConvos.get(0);
        var linkedMessages = messageRepository.findByContactId(anyGroup.getParticipants().iterator().next().getId(), org.springframework.data.domain.Pageable.ofSize(5));
        // Ensure messages have conversation non-null
        var allMessages = messageRepository.findByTimestampBetween(java.time.Instant.EPOCH, java.time.Instant.now());
        Assertions.assertThat(allMessages).isNotEmpty();
        Assertions.assertThat(allMessages.stream().filter(m -> m.getProtocol() != null && m.getProtocol().name().equals("MMS"))).isNotEmpty();
        Assertions.assertThat(allMessages.stream().map(m -> m.getConversation()).filter(java.util.Objects::nonNull).count()).isGreaterThan(0);
        Assertions.assertThat(allMessages.stream().filter(m -> m.getProtocol().name().equals("MMS") && m.getConversation()==null).count()).isEqualTo(0);
    }
}

