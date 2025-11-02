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
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
        void importsGroupChatAndCreatesConversation() throws Exception {
        Path xml = Path.of("src/main/resources/test_files/sms-20251101215610.xml");
        UUID jobId = importService.startImportAsync(xml);
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            var p = importService.getProgress(jobId);
            return p != null && ("COMPLETED".equals(p.getStatus()) || "FAILED".equals(p.getStatus()));
        });
        var progress = importService.getProgress(jobId);
        Assertions.assertThat(progress.getStatus()).isEqualTo("COMPLETED");
        Assertions.assertThat(progress.getImportedMessages()).isGreaterThan(0);

        // Verify conversations were created
        var allConversations = conversationRepository.findAllByUserOrderByLastMessage(testUser);
        Assertions.assertThat(allConversations).isNotEmpty();

        long convoCount = allConversations.size();
        Assertions.assertThat(convoCount).as("At least one conversation should be created").isGreaterThanOrEqualTo(1);

        // Verify group conversations exist
        var groupConvos = allConversations.stream()
                .filter(c -> c.getType() == ConversationType.GROUP)
                .toList();
        Assertions.assertThat(groupConvos).as("Group conversations should be created for MMS messages").isNotEmpty();

        // Verify messages were linked to conversations
        var allMessages = messageRepository.findByTimestampBetween(java.time.Instant.EPOCH, java.time.Instant.now());
        Assertions.assertThat(allMessages).as("Messages should be imported").isNotEmpty();

        var mmsMessages = allMessages.stream()
                .filter(m -> m.getProtocol() != null && m.getProtocol().name().equals("MMS"))
                .toList();
        Assertions.assertThat(mmsMessages).as("MMS messages should be imported").isNotEmpty();

        long messagesWithConversation = allMessages.stream()
                .filter(m -> m.getConversation() != null)
                .count();
        Assertions.assertThat(messagesWithConversation)
                .as("All messages should be linked to conversations")
                .isGreaterThan(0);

        long mmsMessagesWithoutConversation = mmsMessages.stream()
                .filter(m -> m.getConversation() == null)
                .count();
        Assertions.assertThat(mmsMessagesWithoutConversation)
                .as("All MMS messages should have a conversation assigned")
                .isEqualTo(0);
    }
}

