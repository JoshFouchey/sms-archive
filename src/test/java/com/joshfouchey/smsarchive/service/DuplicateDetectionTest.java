package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.*;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Tests for duplicate detection improvements including:
 * - Checking duplicates in current batch before database
 * - Including user in duplicate detection
 * - Case-insensitive body comparison
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DuplicateDetectionTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ContactRepository contactRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ThumbnailService thumbnailService;
    @Mock
    private ConversationService conversationService;
    @Mock
    private UserRepository userRepository;

    private ImportService service;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");

        when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
        when(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.of(testUser));

        // Mock contact repository
        lenient().when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> {
            var c = (Contact) inv.getArgument(0);
            if (c.getId() == null) c.setId((long)(Math.random() * 1000));
            return c;
        });

        // Mock conversation service
        lenient().when(conversationService.findOrCreateOneToOneForUser(any(), any(), any())).thenAnswer(inv -> {
            var conv = new Conversation();
            conv.setId(1L);
            conv.setUser(testUser);
            conv.setName(inv.getArgument(1));
            return conv;
        });
        lenient().when(conversationService.findOrCreateGroupForUser(any(), any(), any(), any())).thenAnswer(inv -> {
            var conv = new Conversation();
            conv.setId(2L);
            conv.setUser(testUser);
            return conv;
        });
        lenient().when(conversationService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Mock duplicate checks - return false (no duplicates in DB)
        lenient().when(messageRepository.existsByConversationAndTimestampAndBody(any(), any(), any(), any(), any(), any())).thenReturn(false);
        lenient().when(messageRepository.existsByTimestampAndBody(any(), any(), any(), any(), any())).thenReturn(false);

        // Capture saved messages
        lenient().when(messageRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service = Mockito.spy(new ImportService(messageRepository, contactRepository, currentUserProvider, thumbnailService, conversationService, userRepository));
        Files.createDirectories(Path.of("test-media-root"));
        Mockito.doReturn(Path.of("test-media-root")).when(service).getMediaRoot();
    }

    @Test
    @DisplayName("Should detect duplicates within same batch (in-memory check)")
    void testDuplicatesInSameBatch() throws Exception {
        // XML with exact duplicates within the file
        String xml = """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <smses count="4">
              <sms protocol="0" address="5551234567" date="1444417958196" type="1" body="Hello" />
              <sms protocol="0" address="5551234567" date="1444417958196" type="1" body="Hello" />
              <sms protocol="0" address="5551234567" date="1444418000000" type="2" body="World" />
              <sms protocol="0" address="5551234567" date="1444418000000" type="2" body="World" />
            </smses>
            """;

        Path xmlFile = Path.of("test-duplicates-batch.xml");
        Files.writeString(xmlFile, xml);

        try {
            UUID jobId = service.startImportAsyncForUser(xmlFile, "testuser");

            // Wait for completion
            int maxWait = 200;
            while (maxWait-- > 0 && !"COMPLETED".equals(service.getProgress(jobId).getStatus())) {
                Thread.sleep(25);
            }

            ImportService.ImportProgress progress = service.getProgress(jobId);
            assertThat(progress.getStatus()).isEqualTo("COMPLETED");
            assertThat(progress.getProcessedMessages()).isEqualTo(4);
            assertThat(progress.getImportedMessages()).isEqualTo(2);
            assertThat(progress.getDuplicateMessages()).isEqualTo(2);
        } finally {
            Files.deleteIfExists(xmlFile);
        }
    }

    @Test
    @DisplayName("Should detect case-insensitive duplicates")
    void testCaseInsensitiveDuplicates() throws Exception {
        // XML with case variations of same message
        String xml = """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <smses count="3">
              <sms protocol="0" address="5551234567" date="1444417958196" type="1" body="Hello World" />
              <sms protocol="0" address="5551234567" date="1444417958196" type="1" body="hello world" />
              <sms protocol="0" address="5551234567" date="1444417958196" type="1" body="HELLO WORLD" />
            </smses>
            """;

        Path xmlFile = Path.of("test-case-duplicates.xml");
        Files.writeString(xmlFile, xml);

        try {
            UUID jobId = service.startImportAsyncForUser(xmlFile, "testuser");

            // Wait for completion
            int maxWait = 200;
            while (maxWait-- > 0 && !"COMPLETED".equals(service.getProgress(jobId).getStatus())) {
                Thread.sleep(25);
            }

            ImportService.ImportProgress progress = service.getProgress(jobId);
            assertThat(progress.getStatus()).isEqualTo("COMPLETED");
            assertThat(progress.getProcessedMessages()).isEqualTo(3);
            assertThat(progress.getImportedMessages()).isEqualTo(1);
            assertThat(progress.getDuplicateMessages()).isEqualTo(2);
        } finally {
            Files.deleteIfExists(xmlFile);
        }
    }

    @Test
    @DisplayName("Should NOT mark as duplicate if message direction differs")
    void testDifferentDirectionNotDuplicate() throws Exception {
        // XML with same body/timestamp but different directions
        String xml = """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <smses count="2">
              <sms protocol="0" address="5551234567" date="1444417958196" type="1" body="Hello" />
              <sms protocol="0" address="5551234567" date="1444417958196" type="2" body="Hello" />
            </smses>
            """;

        Path xmlFile = Path.of("test-direction-not-dup.xml");
        Files.writeString(xmlFile, xml);

        try {
            UUID jobId = service.startImportAsyncForUser(xmlFile, "testuser");

            // Wait for completion
            int maxWait = 200;
            while (maxWait-- > 0 && !"COMPLETED".equals(service.getProgress(jobId).getStatus())) {
                Thread.sleep(25);
            }

            ImportService.ImportProgress progress = service.getProgress(jobId);
            assertThat(progress.getStatus()).isEqualTo("COMPLETED");
            assertThat(progress.getProcessedMessages()).isEqualTo(2);
            assertThat(progress.getImportedMessages()).isEqualTo(2);
            assertThat(progress.getDuplicateMessages()).isEqualTo(0);
        } finally {
            Files.deleteIfExists(xmlFile);
        }
    }

    @Test
    @DisplayName("Should handle whitespace variations in body")
    void testWhitespaceTrimming() throws Exception {
        // XML with whitespace variations
        String xml = """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <smses count="3">
              <sms protocol="0" address="5551234567" date="1444417958196" type="1" body="Hello" />
              <sms protocol="0" address="5551234567" date="1444417958196" type="1" body="  Hello  " />
              <sms protocol="0" address="5551234567" date="1444417958196" type="1" body="Hello " />
            </smses>
            """;

        Path xmlFile = Path.of("test-whitespace-dup.xml");
        Files.writeString(xmlFile, xml);

        try {
            UUID jobId = service.startImportAsyncForUser(xmlFile, "testuser");

            // Wait for completion
            int maxWait = 200;
            while (maxWait-- > 0 && !"COMPLETED".equals(service.getProgress(jobId).getStatus())) {
                Thread.sleep(25);
            }

            ImportService.ImportProgress progress = service.getProgress(jobId);
            assertThat(progress.getStatus()).isEqualTo("COMPLETED");
            assertThat(progress.getProcessedMessages()).isEqualTo(3);
            assertThat(progress.getImportedMessages()).isEqualTo(1);
            assertThat(progress.getDuplicateMessages()).isEqualTo(2);
        } finally {
            Files.deleteIfExists(xmlFile);
        }
    }
}

