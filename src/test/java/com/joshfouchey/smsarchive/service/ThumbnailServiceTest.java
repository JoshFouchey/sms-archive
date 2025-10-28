package com.joshfouchey.smsarchive.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ThumbnailService.
 *
 * Test outline:
 * - testCreateThumbnail_SupportedType_Success
 * - testCreateThumbnail_AlreadyExists_SkipWhenNotForced
 * - testCreateThumbnail_AlreadyExists_RegenerateWhenForced
 * - testCreateThumbnail_OriginalMissing_ReturnsFalse
 * - testCreateThumbnail_UnsupportedType_GeneratesPlaceholder
 * - testCreateThumbnail_InvalidContentType_SkipsGracefully
 * - testDeriveThumbnailPath_ValidInput_ReturnsCorrectPath
 * - testIsSupported_VariousContentTypes
 * - testIsUnsupportedNeedsPlaceholder_HeicHeif
 */
class ThumbnailServiceTest {

    private ThumbnailService thumbnailService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        thumbnailService = new ThumbnailService();
    }

    @Test
    void testIsSupported_SupportedTypes() {
        assertTrue(thumbnailService.isSupported("image/jpeg"));
        assertTrue(thumbnailService.isSupported("image/jpg"));
        assertTrue(thumbnailService.isSupported("image/png"));
        assertTrue(thumbnailService.isSupported("image/gif"));
        assertTrue(thumbnailService.isSupported("image/bmp"));
        assertTrue(thumbnailService.isSupported("IMAGE/JPEG")); // case insensitive
    }

    @Test
    void testIsSupported_UnsupportedTypes() {
        assertFalse(thumbnailService.isSupported("image/heic"));
        assertFalse(thumbnailService.isSupported("video/mp4"));
        assertFalse(thumbnailService.isSupported(null));
        assertFalse(thumbnailService.isSupported(""));
    }

    @Test
    void testIsUnsupportedNeedsPlaceholder_HeicHeif() {
        assertTrue(thumbnailService.isUnsupportedNeedsPlaceholder("image/heic"));
        assertTrue(thumbnailService.isUnsupportedNeedsPlaceholder("image/heif"));
        assertTrue(thumbnailService.isUnsupportedNeedsPlaceholder("IMAGE/HEIC")); // case insensitive
        assertFalse(thumbnailService.isUnsupportedNeedsPlaceholder("image/jpeg"));
        assertFalse(thumbnailService.isUnsupportedNeedsPlaceholder(null));
    }

    @Test
    void testDeriveThumbnailPath_ValidInput() {
        Path original = tempDir.resolve("part0.jpg");
        Path thumb = thumbnailService.deriveThumbnailPath(original, 0);

        assertEquals("part0_thumb.jpg", thumb.getFileName().toString());
        assertEquals(tempDir, thumb.getParent());
    }

    @Test
    void testDeriveThumbnailPath_InvalidInput() {
        assertThrows(IllegalArgumentException.class,
            () -> thumbnailService.deriveThumbnailPath(null, 0));
    }

    // TODO: Implement remaining tests with actual image file generation
    // - testCreateThumbnail_SupportedType_Success (requires creating test PNG/JPG)
    // - testCreateThumbnail_AlreadyExists_SkipWhenNotForced
    // - testCreateThumbnail_AlreadyExists_RegenerateWhenForced
    // - testCreateThumbnail_OriginalMissing_ReturnsFalse
    // - testCreateThumbnail_UnsupportedType_GeneratesPlaceholder
    // - testCreateUnsupportedPlaceholder_CreatesFile
}

