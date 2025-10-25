package com.joshfouchey.smsarchive.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ThumbnailJobProgress.
 *
 * Test outline:
 * - testProgressTracking_AtomicUpdates
 * - testPercentComplete_Calculation
 * - testPercentComplete_ZeroTotal_ReturnsZero
 * - testThreadSafety_ConcurrentUpdates
 * - testErrorTracking_ThreadSafe
 * - testStatusTransitions_PendingToRunningToCompleted
 */
class ThumbnailJobProgressTest {

    private ThumbnailJobProgress progress;

    @BeforeEach
    void setUp() {
        progress = new ThumbnailJobProgress();
    }

    @Test
    void testInitialState() {
        assertNotNull(progress.getId());
        assertEquals("PENDING", progress.getStatus());
        assertEquals(0, progress.getTotalParts());
        assertEquals(0, progress.getProcessedParts());
        assertEquals(0, progress.getRegeneratedThumbnails());
        assertEquals(0, progress.getSkippedThumbnails());
        assertTrue(progress.getErrors().isEmpty());
        assertNull(progress.getStartedAt());
        assertNull(progress.getFinishedAt());
    }

    @Test
    void testProgressTracking_BasicFlow() {
        progress.setTotal(100);
        progress.start();

        assertEquals("RUNNING", progress.getStatus());
        assertNotNull(progress.getStartedAt());
        assertEquals(100, progress.getTotalParts());

        progress.incProcessed();
        progress.incRegenerated();

        assertEquals(1, progress.getProcessedParts());
        assertEquals(1, progress.getRegeneratedThumbnails());

        progress.incProcessed();
        progress.incSkipped();

        assertEquals(2, progress.getProcessedParts());
        assertEquals(1, progress.getSkippedThumbnails());

        progress.finish("COMPLETED");
        assertEquals("COMPLETED", progress.getStatus());
        assertNotNull(progress.getFinishedAt());
    }

    @Test
    void testPercentComplete_Calculation() {
        progress.setTotal(100);
        assertEquals(0.0, progress.getPercentComplete());

        for (int i = 0; i < 50; i++) {
            progress.incProcessed();
        }
        assertEquals(50.0, progress.getPercentComplete(), 0.01);

        for (int i = 0; i < 50; i++) {
            progress.incProcessed();
        }
        assertEquals(100.0, progress.getPercentComplete(), 0.01);
    }

    @Test
    void testPercentComplete_ZeroTotal_ReturnsZero() {
        progress.setTotal(0);
        assertEquals(0.0, progress.getPercentComplete());

        progress.incProcessed(); // shouldn't cause divide-by-zero
        assertEquals(0.0, progress.getPercentComplete());
    }

    @Test
    void testErrorTracking() {
        progress.addError("Error 1");
        progress.addError("Error 2");

        assertEquals(2, progress.getErrors().size());
        assertTrue(progress.getErrors().contains("Error 1"));
        assertTrue(progress.getErrors().contains("Error 2"));
    }

    @Test
    void testStatusTransitions() {
        assertEquals("PENDING", progress.getStatus());

        progress.start();
        assertEquals("RUNNING", progress.getStatus());

        progress.finish("COMPLETED");
        assertEquals("COMPLETED", progress.getStatus());

        // Test FAILED status
        ThumbnailJobProgress failedProgress = new ThumbnailJobProgress();
        failedProgress.start();
        failedProgress.finish("FAILED");
        assertEquals("FAILED", failedProgress.getStatus());
    }

    // TODO: Implement concurrent update tests to verify thread safety
    // - testThreadSafety_ConcurrentIncrements (requires ExecutorService)
    // - testThreadSafety_ConcurrentErrorAdds
}

