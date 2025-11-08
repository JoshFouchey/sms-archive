package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Focused fast unit tests for contact name sanitization and group name heuristics.
 * Uses reflection to access private helper methods; avoids any XML parsing/import work.
 */
class ImportServiceNameSanitizationTest {

    private ImportService service;
    private CurrentUserProvider currentUserProvider;

    private Method sanitizeMethod;
    private Method isGroupLikeMethod;

    @BeforeEach
    void setup() throws Exception {
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        ContactRepository contactRepository = Mockito.mock(ContactRepository.class);
        ThumbnailService thumbnailService = Mockito.mock(ThumbnailService.class);
        ConversationService conversationService = Mockito.mock(ConversationService.class);
        currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        com.joshfouchey.smsarchive.model.User testUser = new com.joshfouchey.smsarchive.model.User();
        testUser.setId(java.util.UUID.randomUUID());
        testUser.setUsername("tester");
        when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
        // minimal conversation stubs (not used directly but constructor requires service)
        when(conversationService.findOrCreateOneToOneForUser(testUser, "123", "Bob"))
                .thenReturn(Conversation.builder().id(1L).user(testUser).name("Bob").build());
        service = Mockito.spy(new ImportService(messageRepository, contactRepository, currentUserProvider, thumbnailService, conversationService));
        doReturn(Path.of("test-media-root")).when(service).getMediaRoot();
        sanitizeMethod = ImportService.class.getDeclaredMethod("sanitizeContactName", String.class);
        sanitizeMethod.setAccessible(true);
        isGroupLikeMethod = ImportService.class.getDeclaredMethod("isGroupLikeName", String.class);
        isGroupLikeMethod.setAccessible(true);
    }

    @Test
    @DisplayName("sanitizeContactName returns null for group-like names")
    void sanitizeRejectsGroupLikeNames() throws Exception {
        assertNull(sanitizeMethod.invoke(service, "Vacation Group Chat"));
        assertNull(sanitizeMethod.invoke(service, "Team Alpha Chat"));
        assertNull(sanitizeMethod.invoke(service, "Project Team Meeting"));
    }

    @Test
    @DisplayName("isGroupLikeName detects multi-word group keywords")
    void groupLikeDetectionWorks() throws Exception {
        assertTrue((Boolean) isGroupLikeMethod.invoke(service, "Vacation Group Chat"));
        assertTrue((Boolean) isGroupLikeMethod.invoke(service, "Team Alpha")); // has keyword + >=2 words
        assertTrue((Boolean) isGroupLikeMethod.invoke(service, "Alpha Team"));
        assertTrue((Boolean) isGroupLikeMethod.invoke(service, "Study Group"));
        // Negative cases
        assertFalse((Boolean) isGroupLikeMethod.invoke(service, "Group")); // single word only
        assertFalse((Boolean) isGroupLikeMethod.invoke(service, "Chat"));
        assertFalse((Boolean) isGroupLikeMethod.invoke(service, "Alice Bob")); // no keywords
        assertFalse((Boolean) isGroupLikeMethod.invoke(service, "Bob")); // single word name
    }

    @Test
    @DisplayName("sanitizeContactName keeps real person names and trims whitespace")
    void sanitizeKeepsPersonNames() throws Exception {
        assertEquals("Alice", sanitizeMethod.invoke(service, " Alice \n"));
        assertEquals("Bob Jones", sanitizeMethod.invoke(service, "Bob Jones"));
        assertEquals("Charlie", sanitizeMethod.invoke(service, "Charlie"));
    }

    @Test
    @DisplayName("sanitizeContactName filters unknown patterns")
    void sanitizeUnknownPatterns() throws Exception {
        assertNull(sanitizeMethod.invoke(service, "unknown"));
        assertNull(sanitizeMethod.invoke(service, "(unknown)"));
        assertNull(sanitizeMethod.invoke(service, "(some unknown contact)"));
        assertNull(sanitizeMethod.invoke(service, "   UNKNOWN   "));
    }

    @Test
    @DisplayName("isGroupLikeName is case-insensitive")
    void groupLikeCaseInsensitive() throws Exception {
        assertTrue((Boolean) isGroupLikeMethod.invoke(service, "TEAM alpha"));
        assertTrue((Boolean) isGroupLikeMethod.invoke(service, "VaCaTiOn group"));
    }
}

