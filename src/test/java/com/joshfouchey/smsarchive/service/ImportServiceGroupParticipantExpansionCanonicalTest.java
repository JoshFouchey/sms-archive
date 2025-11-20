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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Same scenario as ImportServiceGroupParticipantExpansionTest but asserts canonical 11-digit normalization.
 */
class ImportServiceGroupParticipantExpansionCanonicalTest {
    private ImportService service;
    private MessageRepository messageRepository;
    private ContactRepository contactRepository;
    private CurrentUserProvider currentUserProvider;
    private ThumbnailService thumbnailService;
    private UserRepository userRepository;
    private ConversationService conversationService;

    private final List<Set<String>> capturedParticipantSets = new ArrayList<>();

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
        testUser.setUsername("tester");
        when(currentUserProvider.getCurrentUser()).thenReturn(testUser);

        AtomicInteger contactId = new AtomicInteger(1);
        when(contactRepository.findByUserAndNormalizedNumber(any(), anyString())).thenReturn(Optional.empty());
        when(contactRepository.save(any())).thenAnswer(inv -> {
            var c = (com.joshfouchey.smsarchive.model.Contact) inv.getArgument(0);
            if (c.getId() == null) c.setId((long) contactId.getAndIncrement());
            return c;
        });

        when(conversationService.findOrCreateGroupForUser(any(User.class), anyString(), anySet(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked") Set<String> participants = inv.getArgument(2);
                    capturedParticipantSets.add(new LinkedHashSet<>(participants));
                    Conversation conv = new Conversation();
                    conv.setId(200L + capturedParticipantSets.size());
                    conv.setUser(inv.getArgument(0));
                    conv.setThreadKey(inv.getArgument(1));
                    conv.setName(inv.getArgument(3));
                    Set<com.joshfouchey.smsarchive.model.Contact> contactObjs = new LinkedHashSet<>();
                    for (String norm : participants) {
                        com.joshfouchey.smsarchive.model.Contact c = new com.joshfouchey.smsarchive.model.Contact();
                        c.setId((long) contactId.getAndIncrement());
                        c.setUser(testUser);
                        c.setNormalizedNumber(norm);
                        c.setNumber(norm);
                        contactObjs.add(c);
                    }
                    conv.setParticipants(contactObjs);
                    return conv;
                });

        when(conversationService.findOrCreateOneToOneForUser(any(User.class), anyString(), any()))
                .thenAnswer(inv -> {
                    String norm = inv.getArgument(1);
                    Conversation conv = new Conversation();
                    conv.setId(2L);
                    conv.setUser(inv.getArgument(0));
                    conv.setName(norm);
                    return conv;
                });

        when(conversationService.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.existsByConversationAndTimestampAndBody(any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(messageRepository.existsByTimestampAndBody(any(), any(), any(), any(), any())).thenReturn(false);

        service = Mockito.spy(new ImportService(messageRepository, contactRepository, currentUserProvider, thumbnailService, conversationService, userRepository));
        doReturn(Path.of("test-media-root")).when(service).getMediaRoot();
        Files.createDirectories(Path.of("test-media-root"));
    }

    @Test
    @DisplayName("Group participant normalization produces canonical 11-digit numbers")
    void canonicalGroupParticipants() throws Exception {
        String xml = "<root>" +
                "<mms date=\"0\" msg_box=\"1\" contact_name=\"Test Group\" address=\"thread123\">" +
                "  <addr address=\"1234567890\" type=\"137\"/>" +
                "  <addr address=\"1112223333\" type=\"130\"/>" +
                "  <addr address=\"me\" type=\"151\"/>" +
                "  <part seq=\"0\" ct=\"text/plain\" text=\"Hello inbound\"/>" +
                "</mms>" +
                "<mms date=\"1\" msg_box=\"2\" contact_name=\"Test Group\" address=\"thread123\">" +
                "  <addr address=\"1112223333\" type=\"151\"/>" +
                "  <addr address=\"9998887777\" type=\"151\"/>" +
                "  <part seq=\"0\" ct=\"text/plain\" text=\"Hello outbound\"/>" +
                "</mms>" +
                "</root>";

        Path temp = Files.createTempFile("group-test", ".xml");
        Files.writeString(temp, xml);

        var jobId = service.startImportAsync(temp);
        assertThat(jobId).isNotNull();

        ImportService.ImportProgress progress;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            progress = service.getProgress(jobId);
            if (progress != null && "COMPLETED".equals(progress.getStatus())) break;
            Thread.sleep(25);
        }
        progress = service.getProgress(jobId);
        assertThat(progress).isNotNull();
        assertEquals("COMPLETED", progress.getStatus());

        assertEquals(2, capturedParticipantSets.size());
        Set<String> inboundParticipants = capturedParticipantSets.get(0);
        Set<String> outboundParticipants = capturedParticipantSets.get(1);

        assertThat(inboundParticipants).containsExactlyInAnyOrder("11234567890", "11112223333");
        assertTrue(!inboundParticipants.contains("me"));
        assertThat(outboundParticipants).containsExactlyInAnyOrder("11112223333", "19998887777");
        assertTrue(!outboundParticipants.contains("11234567890"));
    }
}

