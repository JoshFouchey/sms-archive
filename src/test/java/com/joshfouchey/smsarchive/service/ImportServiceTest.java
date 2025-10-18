package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessageDirection;
import com.joshfouchey.smsarchive.model.MessageProtocol;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ImportServiceTest {

    private ImportService service;
    private MessageRepository messageRepository;
    private ContactRepository contactRepository;

    @BeforeEach
    void setup() {
        messageRepository = Mockito.mock(MessageRepository.class);
        contactRepository = Mockito.mock(ContactRepository.class);
        service = new ImportService(messageRepository, contactRepository);
    }

    @Test
    @DisplayName("normalizeNumber strips non-digits and US country code")
    void testNormalizeNumber() {
        assertEquals("5551234567", service.normalizeNumber("+1 (555) 123-4567"));
        assertEquals("5551234567", service.normalizeNumber("1-555-123-4567"));
        assertEquals("", service.normalizeNumber(null));
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
}

