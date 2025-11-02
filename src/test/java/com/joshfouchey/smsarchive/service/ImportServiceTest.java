package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessageDirection;
import com.joshfouchey.smsarchive.model.MessageProtocol;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.ConversationRepository;
import com.joshfouchey.smsarchive.repository.ConversationMessageRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Combined ImportService tests (unit + integration) in one class.
 */
class ImportServiceTest {

    // ------------------------ UNIT TESTS (mock-based) ------------------------
    @Nested
    class UnitTests {
        private ImportService service;
        private MessageRepository messageRepository;
        private ContactRepository contactRepository;
        private CurrentUserProvider currentUserProvider;
        private ThumbnailService thumbnailService;
        private ConversationRepository conversationRepository;
        private ConversationMessageRepository conversationMessageRepository;

        @BeforeEach
        void setup() {
            messageRepository = Mockito.mock(MessageRepository.class);
            contactRepository = Mockito.mock(ContactRepository.class);
            currentUserProvider = Mockito.mock(CurrentUserProvider.class);
            thumbnailService = Mockito.mock(ThumbnailService.class);
            conversationRepository = Mockito.mock(ConversationRepository.class);
            conversationMessageRepository = Mockito.mock(ConversationMessageRepository.class);

            // Mock the current user
            com.joshfouchey.smsarchive.model.User testUser = new com.joshfouchey.smsarchive.model.User();
            testUser.setId(java.util.UUID.randomUUID());
            testUser.setUsername("testuser");
            when(currentUserProvider.getCurrentUser()).thenReturn(testUser);

            service = Mockito.spy(new ImportService(messageRepository, contactRepository, currentUserProvider, thumbnailService, conversationRepository, conversationMessageRepository));
            doReturn(Path.of("test-media-root")).when(service).getMediaRoot(); // Mock media root
        }

        @Test
        @DisplayName("normalizeNumber strips non-digits and US country code")
        void testNormalizeNumber() {
            assertEquals("15551234567", service.normalizeNumber("+1 (555) 123-4567"));
            assertEquals("15551234567", service.normalizeNumber("1-555-123-4567"));
            assertEquals("__unknown__", service.normalizeNumber(null));
            assertEquals("42", service.normalizeNumber(" 42 "));
            assertEquals("123456789012", service.normalizeNumber("123456789012")); // >11 digits not trimmed other than non-digits
        }

        @Test
        @DisplayName("guessExtension prefers filename extension if present")
        void testGuessExtensionNameWins() {
            assertEquals(".png", service.guessExtension("image/jpeg", "photo.png")); // name overrides content-type
            assertEquals(".txt", service.guessExtension("text/plain", "note.TXT")); // case-insensitive extension
        }

        @Test
        @DisplayName("guessExtension falls back to content-type map and .bin")
        void testGuessExtensionContentTypeFallback() {
            assertEquals(".jpg", service.guessExtension("image/jpeg", null));
            assertEquals(".mp4", service.guessExtension("video/mp4", "clip"));
            assertEquals(".bin", service.guessExtension(null, null));
            assertEquals(".bin", service.guessExtension("application/octet-stream", "file"));
        }

        @Test
        @DisplayName("parseInstant handles epoch millis, epoch seconds, ISO8601, and bad input")
        void testParseInstant() {
            Instant now = Instant.now();
            String iso = now.toString();
            assertEquals(now, service.parseInstant(iso));

            long ms = 1700000000000L; // millis
            assertEquals(Instant.ofEpochMilli(ms), service.parseInstant(String.valueOf(ms)));

            long seconds = 1700000000L; // 10 digits -> seconds
            assertEquals(Instant.ofEpochSecond(seconds), service.parseInstant(String.valueOf(seconds)));

            assertEquals(Instant.EPOCH, service.parseInstant("not-a-date"));
            assertEquals(Instant.EPOCH, service.parseInstant(""));
            assertEquals(Instant.EPOCH, service.parseInstant(null));
        }

        @Test
        @DisplayName("duplicate key stable for trimmed body and null contact")
        void testDuplicateKeyStable() {
            Message m = new Message();
            m.setProtocol(MessageProtocol.SMS);
            m.setDirection(MessageDirection.INBOUND);
            m.setTimestamp(Instant.ofEpochMilli(123456789));
            m.setMsgBox(1);
            m.setBody("  Hello World  ");
            String key1 = service.computeDuplicateKeyForTest(m);
            m.setBody("Hello World");
            String key2 = service.computeDuplicateKeyForTest(m);
            assertEquals(key1, key2, "Body trimming should not change duplicate key");

            // With a contact id, key should change
            Contact c = Contact.builder().id(42L).number("5551234567").normalizedNumber("5551234567").build();
            m.setContact(c);
            String key3 = service.computeDuplicateKeyForTest(m);
            assertNotEquals(key1, key3);
            assertTrue(key3.startsWith("42|"), "Contact id should prefix the key");
        }

        @Test
        @DisplayName("streaming import (mock) processes all messages and tracks progress")
        void streamingImportProcessesAllMessagesAndTracksProgress() throws Exception {
            // Mock repos behavior similar to former StreamingImportServiceTest
            when(contactRepository.findByNormalizedNumber(anyString())).thenReturn(java.util.Optional.empty());
            when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> {
                Contact c = inv.getArgument(0);
                if (c.getId() == null) c.setId(1L);
                return c;
            });
            when(messageRepository.existsDuplicate(any(), any(), anyInt(), any(), any())).thenReturn(false);
            when(conversationRepository.findByUserAndExternalThreadId(any(), any())).thenReturn(java.util.Optional.empty());
            when(conversationMessageRepository.existsConversationDuplicate(anyLong(), any(), anyInt(), any(), any())).thenReturn(false);
            // Capture saved messages count
            final java.util.concurrent.atomic.AtomicInteger saved = new java.util.concurrent.atomic.AtomicInteger();
            doAnswer(inv -> {
                @SuppressWarnings("unchecked") java.util.List<Message> batch = (java.util.List<Message>) inv.getArgument(0);
                saved.addAndGet(batch.size());
                return null;
            }).when(messageRepository).saveAll(anyList());
            when(conversationRepository.save(any())).thenAnswer(inv -> {
                com.joshfouchey.smsarchive.model.Conversation c = inv.getArgument(0);
                if (c.getId() == null) c.setId(100L); // arbitrary test id
                return c;
            });

            Path xml = Path.of("src/test/resources/test-streaming-large.xml");
            assertThat(xml).exists();
            var jobId = service.startImportAsync(xml);
            assertThat(jobId).isNotNull();
            ImportService.ImportProgress progress = null;
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 5000) {
                progress = service.getProgress(jobId);
                if (progress != null && ("COMPLETED".equals(progress.getStatus()) || "FAILED".equals(progress.getStatus()))) {
                    break;
                }
                Thread.sleep(50);
            }
            assertThat(progress).isNotNull();
            assertThat(progress.getStatus()).isEqualTo("COMPLETED");
            assertThat(progress.getImportedMessages()).isEqualTo(5);
            assertThat(saved.get()).isEqualTo(5);
        }
    }

    // ------------------------ INTEGRATION TESTS (Spring + DB) ------------------------
    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @org.springframework.security.test.context.support.WithMockUser(username = "testuser")
    class IntegrationTests extends EnhancedPostgresTestContainer {
        @Autowired ImportService importService;
        @Autowired MessageRepository messageRepository;
        @Autowired MessagePartRepository messagePartRepository;
        @Autowired ContactRepository contactRepository;
        @Autowired com.joshfouchey.smsarchive.repository.UserRepository userRepository;
        @Autowired ConversationRepository conversationRepository;

        private com.joshfouchey.smsarchive.model.User testUser;

        @BeforeEach
        void cleanDb() {
            // Order matters due to FK relationships
            messagePartRepository.deleteAll();
            messageRepository.deleteAll();
            contactRepository.deleteAll();
            userRepository.deleteAll();

            testUser = new com.joshfouchey.smsarchive.model.User();
            testUser.setUsername("testuser");
            testUser.setPasswordHash("$2a$10$dummyhash");
            testUser = userRepository.save(testUser);
        }

        private ImportService.ImportProgress awaitCompletion(UUID job) {
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> {
                var prog = importService.getProgress(job);
                return prog != null && ("COMPLETED".equals(prog.getStatus()) || "FAILED".equals(prog.getStatus()));
            });
            return importService.getProgress(job);
        }

        @Test
        void emptyFileFailsGracefully() throws Exception {
            File f = Files.createTempFile("empty-import", ".xml").toFile(); // empty content
            UUID job = importService.startImportAsync(f.toPath());
            ImportService.ImportProgress progress = awaitCompletion(job);
            assertThat(progress.getStatus()).isEqualTo("FAILED");
            assertThat(progress.getImportedMessages()).isZero();
            assertThat(progress.getProcessedMessages()).isZero();
            assertThat(progress.getDuplicateMessagesFinal()).isZero();
            assertThat(progress.getError()).isNotBlank();
        }

        @Test
        void malformedXmlFailsAndDoesNotPersist() throws Exception {
            String malformed = "<?xml version=\"1.0\"?><messages><sms"; // truncated
            File f = Files.createTempFile("malformed-import", ".xml").toFile();
            try (FileWriter fw = new FileWriter(f)) { fw.write(malformed); }
            long before = messageRepository.count();
            UUID job = importService.startImportAsync(f.toPath());
            ImportService.ImportProgress progress = awaitCompletion(job);
            assertThat(progress.getStatus()).isEqualTo("FAILED");
            assertThat(progress.getImportedMessages()).isZero();
            assertThat(messageRepository.count()).isEqualTo(before);
            assertThat(progress.getError()).isNotBlank();
        }

        @Test
        void duplicateKeyTrimsBodyAndUsesContactIdOrNull() {
            // Use a distinct number not present in streamed XML fixtures to avoid unique constraint collisions
            Contact contact = contactRepository.save(Contact.builder().number("+15550000001").normalizedNumber("5550000001").name("TestPerson").user(testUser).build());
            Message msg = new Message();
            msg.setContact(contact);
            msg.setUser(testUser);
            msg.setBody("  Hello World  ");
            msg.setTimestamp(Instant.ofEpochMilli(123456789L));
            msg.setMsgBox(1);
            msg.setDirection(MessageDirection.INBOUND);
            msg.setProtocol(MessageProtocol.SMS);
            String key = importService.computeDuplicateKeyForTest(msg);
            assertThat(key)
                .startsWith(contact.getId().toString() + "|")
                .contains("Hello World")
                .endsWith("Hello World");
            Message msg2 = new Message();
            msg2.setContact(contact);
            msg2.setBody(null);
            msg2.setTimestamp(Instant.ofEpochMilli(123456789L));
            msg2.setMsgBox(1);
            msg2.setDirection(MessageDirection.INBOUND);
            msg2.setProtocol(MessageProtocol.SMS);
            String key2 = importService.computeDuplicateKeyForTest(msg2);
            assertThat(key2).endsWith("|");
            Message msg3 = new Message();
            msg3.setContact(null);
            msg3.setBody("Test");
            msg3.setTimestamp(Instant.ofEpochMilli(123456789L));
            msg3.setMsgBox(1);
            msg3.setDirection(MessageDirection.INBOUND);
            msg3.setProtocol(MessageProtocol.SMS);
            String key3 = importService.computeDuplicateKeyForTest(msg3);
            assertThat(key3).startsWith("null|");
        }

        private File createXmlWithDuplicates() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <messages exported_at="2025-01-01T00:00:00Z">
                      <sms protocol="0" address="+15551234567" date="1696200000000" type="1" body="Hi" contact_name="Alice"/>
                      <sms protocol="0" address="+15551234567" date="1696200000000" type="1" body="Hi" contact_name="Alice"/>
                      <mms date="1696210000000" msg_box="1" contact_name="Bob">
                        <parts>
                          <part seq="0" ct="text/plain" text="Photo"/>
                        </parts>
                        <addrs>
                          <addr type="137" address="+15559876543"/>
                          <addr type="151" address="me"/>
                        </addrs>
                      </mms>
                      <mms date="1696210000000" msg_box="1" contact_name="Bob">
                        <parts>
                          <part seq="0" ct="text/plain" text="Photo"/>
                        </parts>
                        <addrs>
                          <addr type="137" address="+15559876543"/>
                          <addr type="151" address="me"/>
                        </addrs>
                      </mms>
                    </messages>
                    """;
            File f = Files.createTempFile("stream-dup-test", ".xml").toFile();
            try (FileWriter fw = new FileWriter(f)) { fw.write(xml); }
            return f;
        }

        @Test
        void streamingImportDetectsDuplicatesWithinFileAndAcrossRuns() throws Exception {
            File xml = createXmlWithDuplicates();
            long beforeMessages = messageRepository.count();
            long beforeParts = messagePartRepository.count();

            UUID firstJob = importService.startImportAsync(xml.toPath());
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(importService.getProgress(firstJob)).isNotNull();
                assertThat(importService.getProgress(firstJob).getStatus()).isEqualTo("COMPLETED");
            });
            ImportService.ImportProgress first = importService.getProgress(firstJob);
            long importedFirst = first.getImportedMessages();
            long duplicatesFirst = first.getDuplicateMessagesFinal();
            long processedFirst = first.getProcessedMessages();
            assertThat(processedFirst).isEqualTo(4);
            assertThat(importedFirst).isEqualTo(2);
            assertThat(duplicatesFirst).isEqualTo(2);
            long afterFirstMessages = messageRepository.count();
            long afterFirstParts = messagePartRepository.count();
            assertThat(afterFirstMessages - beforeMessages).isEqualTo(importedFirst);
            assertThat(afterFirstParts - beforeParts).isGreaterThanOrEqualTo(1);

            UUID secondJob = importService.startImportAsync(xml.toPath());
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(importService.getProgress(secondJob)).isNotNull();
                assertThat(importService.getProgress(secondJob).getStatus()).isEqualTo("COMPLETED");
            });
            ImportService.ImportProgress second = importService.getProgress(secondJob);
            long importedSecond = second.getImportedMessages();
            long duplicatesSecond = second.getDuplicateMessagesFinal();
            long processedSecond = second.getProcessedMessages();
            assertThat(processedSecond).isEqualTo(4);
            assertThat(importedSecond).isZero();
            assertThat(duplicatesSecond).isEqualTo(4);
            long afterSecondMessages = messageRepository.count();
            long afterSecondParts = messagePartRepository.count();
            assertThat(afterSecondMessages).isEqualTo(afterFirstMessages);
            assertThat(afterSecondParts).isEqualTo(afterFirstParts);
        }

        @Test
        void streamingImportProcessesAllMessagesAndTracksProgressRealRepos() throws Exception {
            Path xml = Path.of("src/test/resources/test-streaming-large.xml");
            assertThat(xml).exists();
            long before = messageRepository.count();
            UUID jobId = importService.startImportAsync(xml);
            ImportService.ImportProgress progress = awaitCompletion(jobId);
            assertThat(progress.getStatus()).isEqualTo("COMPLETED");
            assertThat(progress.getImportedMessages()).isEqualTo(5);
            assertThat(messageRepository.count() - before).isEqualTo(5);
        }

        @Test
        @org.springframework.transaction.annotation.Transactional
        void groupMmsWithParticipantType130CreatesSingleGroupConversation() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <messages exported_at="2025-01-01T00:00:00Z">
                      <mms date="1700000000000" msg_box="1" contact_name="(Unknown)" address="group@rcs.google.com">
                        <parts><part seq="0" ct="text/plain" text="Hello group"/></parts>
                        <addrs>
                          <addr type="137" address="+15551230001"/>
                          <addr type="130" address="+15551230002"/>
                          <addr type="151" address="me"/>
                        </addrs>
                      </mms>
                      <mms date="1700000005000" msg_box="1" contact_name="(Unknown)" address="group@rcs.google.com">
                        <parts><part seq="0" ct="text/plain" text="Second message"/></parts>
                        <addrs>
                          <addr type="137" address="+15551230001"/>
                          <addr type="130" address="+15551230002"/>
                          <addr type="151" address="me"/>
                        </addrs>
                      </mms>
                    </messages>
                    """;
            File f = Files.createTempFile("group-130-test", ".xml").toFile();
            try (FileWriter fw = new FileWriter(f)) { fw.write(xml); }
            UUID job = importService.startImportAsync(f.toPath());
            ImportService.ImportProgress progress = awaitCompletion(job);
            assertThat(progress.getStatus()).isEqualTo("COMPLETED");
            assertThat(progress.getImportedMessages()).isEqualTo(2);
            var allMessages = messageRepository.findAll();
            assertThat(allMessages).hasSize(2);
            var convoIds = allMessages.stream().map(m -> m.getConversation().getId()).distinct().toList();
            assertThat(convoIds).hasSize(1); // single conversation
            // Fetch fully initialized conversation with participants to avoid lazy init issues
            String extId = allMessages.get(0).getConversation().getExternalThreadId();
            var convo = conversationRepository.findWithParticipantsByUserAndExternalThreadId(testUser, extId).orElseThrow();
            assertThat(convo.getType()).isEqualTo("GROUP");
            assertThat(convo.getParticipants()).hasSize(3); // 2 contacts + self
        }
    }
}
