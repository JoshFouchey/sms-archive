package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Ensures MMS arriving before SMS for same number produces a single reused conversation.
 */
class ConversationReuseOrderTest {
    private ImportService service;
    private MessageRepository messageRepository;
    private ContactRepository contactRepository;
    private CurrentUserProvider currentUserProvider;
    private ThumbnailService thumbnailService;
    private UserRepository userRepository;
    private ConversationService conversationService;
    private java.util.Map<String, Conversation> oneToOneMap; // promoted to field

    @BeforeEach
    void setup() throws Exception {
        messageRepository = Mockito.mock(MessageRepository.class);
        contactRepository = Mockito.mock(ContactRepository.class);
        currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        userRepository = Mockito.mock(UserRepository.class);
        thumbnailService = Mockito.mock(ThumbnailService.class);
        conversationService = Mockito.mock(ConversationService.class);

        User testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("orderuser");
        when(currentUserProvider.getCurrentUser()).thenReturn(testUser);

        // Track created conversations by normalized number
        oneToOneMap = new java.util.HashMap<>();
        when(conversationService.findOrCreateOneToOneForUser(any(User.class), anyString(), any()))
                .thenAnswer(inv -> {
                    String norm = inv.getArgument(1);
                    return oneToOneMap.computeIfAbsent(norm, k -> {
                        Conversation c = new Conversation();
                        c.setId((long) (oneToOneMap.size() + 1));
                        c.setUser(inv.getArgument(0));
                        c.setName(norm);
                        // minimal participants set size=1
                        var contact = new com.joshfouchey.smsarchive.model.Contact();
                        contact.setId(999L + oneToOneMap.size());
                        contact.setNormalizedNumber(norm);
                        contact.setNumber(norm);
                        c.setParticipants(Set.of(contact));
                        return c;
                    });
                });
        when(conversationService.findOrCreateGroupForUser(any(User.class), anyString(), anySet(), any()))
                .thenThrow(new IllegalStateException("Group not expected"));
        when(conversationService.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        when(contactRepository.findByUserAndNormalizedNumber(any(), anyString()))
                .thenReturn(Optional.empty());
        when(contactRepository.save(any())).thenAnswer(inv -> {
            var c = (com.joshfouchey.smsarchive.model.Contact) inv.getArgument(0);
            if (c.getId() == null) c.setId(500L + (long)(Math.random()*100));
            return c;
        });
        when(messageRepository.existsByConversationAndTimestampAndBody(any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(messageRepository.existsByTimestampAndBody(any(), any(), any(), any(), any())).thenReturn(false);

        service = Mockito.spy(new ImportService(messageRepository, contactRepository, currentUserProvider, thumbnailService, conversationService, userRepository));
        Files.createDirectories(Path.of("test-media-root"));
        Mockito.doReturn(Path.of("test-media-root")).when(service).getMediaRoot();
    }

    @Test
    @DisplayName("MMS before SMS yields single conversation")
    void mmsBeforeSmsSingleConversation() throws Exception {
        String xml = "<root>" +
                // MMS inbound first
                "<mms date=\"1000\" msg_box=\"1\" contact_name=\"Alice\">" +
                "  <addrs>" +
                "    <addr type=\"137\" address=\"+15551234567\"/>" +
                "    <addr type=\"151\" address=\"me\"/>" +
                "  </addrs>" +
                "  <parts><part seq=\"0\" ct=\"text/plain\" text=\"Photo\"/></parts>" +
                "</mms>" +
                // SMS inbound afterwards
                "<sms protocol=\"0\" address=\"+15551234567\" date=\"2000\" type=\"1\" body=\"Reply\" contact_name=\"Alice\"/>" +
                "</root>";
        Path temp = Files.createTempFile("order-test", ".xml");
        Files.writeString(temp, xml);

        var jobId = service.startImportAsync(temp);
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            var progress = service.getProgress(jobId);
            if (progress != null && "COMPLETED".equals(progress.getStatus())) break;
            Thread.sleep(25);
        }

        // Only one conversation should be created for number 15551234567
        assertThat(oneToOneMap.keySet()).containsExactly("15551234567");
        assertThat(oneToOneMap.values()).hasSize(1);
        Conversation convo = oneToOneMap.values().iterator().next();
        assertThat(convo.getParticipants()).hasSize(1);
    }
}