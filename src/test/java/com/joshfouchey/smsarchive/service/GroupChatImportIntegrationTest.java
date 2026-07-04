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
    @Autowired com.joshfouchey.smsarchive.repository.EmbeddingJobRepository embeddingJobRepository;
    @Autowired org.springframework.cache.CacheManager cacheManager;

    private com.joshfouchey.smsarchive.model.User testUser;

    @BeforeEach
    void setup() {
        messagePartRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        contactRepository.deleteAll();
        // embedding_jobs rows (created by the post-import listener) block user deletion
        embeddingJobRepository.deleteAll();
        userRepository.deleteAll();
        // The singleton ImportService caches Contact entities, and the "currentUser" Spring
        // cache holds User entities - all stale after the wipe above
        importService.clearImportCaches();
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        // Don't create user here - let TestOverridesConfig create it during import
        // to avoid duplicate key constraint violation
    }

    // NOT @Transactional: the import runs on a worker thread with its own DB session,
    // which cannot see rows created in an uncommitted test transaction.
    @Test
    void importsGroupChatAndCreatesConversation() throws Exception {
        Path xml = Path.of("src/test/resources/test-group-chat.xml");
        UUID jobId = importService.startImportAsync(xml);
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            var p = importService.getProgress(jobId);
            return p != null && ("COMPLETED".equals(p.getStatus()) || "FAILED".equals(p.getStatus()));
        });
        var progress = importService.getProgress(jobId);
        Assertions.assertThat(progress.getStatus()).isEqualTo("COMPLETED");
        Assertions.assertThat(progress.getImportedMessages()).isGreaterThan(0);

        // Fetch the user that was created by TestOverridesConfig during import
        testUser = userRepository.findByUsername("testuser").orElseThrow();

        // Verify conversations were created
        var allConversations = conversationRepository.findAllByUserOrderByLastMessage(testUser);
        Assertions.assertThat(allConversations).isNotEmpty();

        long convoCount = allConversations.size();
        Assertions.assertThat(convoCount).as("At least one conversation should be created").isGreaterThanOrEqualTo(1);

        // Verify multi-participant conversations exist (group chats)
        var groupConvos = allConversations.stream()
                .filter(c -> c.getParticipants().size() > 1)
                .toList();
        Assertions.assertThat(groupConvos).as("Multi-participant conversations should be created for MMS messages").isNotEmpty();

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

        // CRITICAL: Verify that group chat names are NOT created as contacts
        // Group chat names like "Neighborhood Group" should ONLY be in Conversation.name, not as Contact records
        var allContacts = contactRepository.findAll();
        var groupNameContacts = allContacts.stream()
                .filter(c -> c.getName() != null &&
                    (c.getName().contains("Group") || c.getName().contains("Team") || c.getName().contains("Vacation")))
                .toList();
        Assertions.assertThat(groupNameContacts)
                .as("Group chat names should NOT be created as contacts - they should only be in Conversation.name")
                .isEmpty();

        // Verify that group conversations DO have the proper names
        var groupConvosWithNames = groupConvos.stream()
                .filter(c -> c.getName() != null && !c.getName().isBlank())
                .toList();
        Assertions.assertThat(groupConvosWithNames)
                .as("Group conversations should have names set")
                .isNotEmpty();
    }
}

