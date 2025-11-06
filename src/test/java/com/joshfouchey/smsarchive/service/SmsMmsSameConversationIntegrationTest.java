package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
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
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "testuser")
public class SmsMmsSameConversationIntegrationTest extends EnhancedPostgresTestContainer {

    @Autowired ImportService importService;
    @Autowired MessageRepository messageRepository;
    @Autowired MessagePartRepository messagePartRepository;
    @Autowired ContactRepository contactRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired UserRepository userRepository;

    private com.joshfouchey.smsarchive.model.User testUser;

    @BeforeEach
    @Transactional
    void setup() {
        messagePartRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        contactRepository.deleteAll();
        userRepository.deleteAll();
        // Don't create user here - let TestOverridesConfig create it during import
        // to avoid duplicate key constraint violation
    }

    private void awaitJob(UUID jobId) {
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> {
            var p = importService.getProgress(jobId);
            return p != null && ("COMPLETED".equals(p.getStatus()) || "FAILED".equals(p.getStatus()));
        });
    }

    @Test
    @Transactional
    void smsAndMmsBetweenSameCounterpartyCreateOnlyOneConversation() throws Exception {
        Path xml = Path.of("src/test/resources/sms_mms_same_conversation.xml");
        UUID jobId = importService.startImportAsync(xml);
        awaitJob(jobId);
        var progress = importService.getProgress(jobId);
        Assertions.assertThat(progress).isNotNull();
        Assertions.assertThat(progress.getStatus()).isEqualTo("COMPLETED");
        Assertions.assertThat(progress.getImportedMessages()).isGreaterThan(0);

        // Fetch the user that was created by TestOverridesConfig during import
        testUser = userRepository.findByUsername("testuser").orElseThrow();

        var conversations = conversationRepository.findAllByUserOrderByLastMessage(testUser);
        System.out.println("Conversation count: " + conversations.size());
        conversations.forEach(c -> {
            System.out.println("Conversation id=" + c.getId() + ", name=" + c.getName() + ", participants=" + c.getParticipants().size());
            c.getParticipants().forEach(p -> System.out.println("  Participant normalized=" + p.getNormalizedNumber() + ", name=" + p.getName()));
        });

        Assertions.assertThat(conversations.size())
                .as("SMS + MMS with same counterparty should map to a single one-to-one conversation")
                .isEqualTo(1);
    }
}

