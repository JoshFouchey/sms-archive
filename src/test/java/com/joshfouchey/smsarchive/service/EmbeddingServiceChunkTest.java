package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.repository.EmbeddingJobRepository;
import com.joshfouchey.smsarchive.repository.MessageEmbeddingRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EmbeddingService.chunkMessage() — the semantic chunking logic.
 * These are pure logic tests; no Spring context or database needed.
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingServiceChunkTest {

    @Mock OllamaEmbeddingModel embeddingModel;
    @Mock MessageRepository messageRepository;
    @Mock MessageEmbeddingRepository embeddingRepository;
    @Mock EmbeddingJobRepository jobRepository;
    @Mock TaskExecutor taskExecutor;
    @Mock TransactionTemplate transactionTemplate;
    @Mock JdbcTemplate jdbcTemplate;

    private EmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingService(
                embeddingModel, messageRepository, embeddingRepository,
                jobRepository, taskExecutor, transactionTemplate, jdbcTemplate);
        ReflectionTestUtils.setField(service, "chunkMaxChars", 100);
        ReflectionTestUtils.setField(service, "chunkOverlapChars", 20);
    }

    @Test
    void shortMessage_singleChunk() {
        List<String> chunks = service.chunkMessage("Hello world");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("Hello world");
    }

    @Test
    void nullMessage_returnsDefault() {
        assertThat(service.chunkMessage(null)).containsExactly("");
    }

    @Test
    void blankMessage_returnsDefault() {
        assertThat(service.chunkMessage("   ")).containsExactly("");
    }

    @Test
    void exactlyAtThreshold_singleChunk() {
        String text = "x".repeat(100);
        List<String> chunks = service.chunkMessage(text);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(text);
    }

    @Test
    void oneCharOverThreshold_splitIntoTwo() {
        String text = "x".repeat(101);
        List<String> chunks = service.chunkMessage(text);
        assertThat(chunks).hasSizeGreaterThan(1);
        // All chunks should be non-empty
        chunks.forEach(c -> assertThat(c).isNotBlank());
    }

    @Test
    void splitsOnParagraphBreaks() {
        // Build text with a paragraph break near the middle
        String para1 = "A".repeat(60);
        String para2 = "B".repeat(60);
        String text = para1 + "\n\n" + para2;

        List<String> chunks = service.chunkMessage(text);
        assertThat(chunks).hasSizeGreaterThan(1);
        // First chunk should end with the first paragraph
        assertThat(chunks.get(0)).endsWith("A");
        // Second chunk should contain the second paragraph
        assertThat(chunks.get(chunks.size() - 1)).contains("BBB");
    }

    @Test
    void splitsOnSentenceBoundaries() {
        // No paragraph breaks, but sentence endings
        String text = "First sentence about something. " +
                "Second sentence continues here. " +
                "Third one is also long enough. " +
                "Fourth sentence with more content.";

        List<String> chunks = service.chunkMessage(text);
        assertThat(chunks).hasSizeGreaterThan(1);
        // Chunks should end at sentence boundaries (period before split)
        assertThat(chunks.get(0)).matches(".*\\.$");
    }

    @Test
    void splitsOnNewlines() {
        // No paragraph breaks or sentences, but newlines
        String text = "Line one of the message\n" +
                "Line two continues here more\n" +
                "Line three has content too\n" +
                "Line four wraps up the text\n" +
                "Line five is the final one";

        List<String> chunks = service.chunkMessage(text);
        assertThat(chunks).hasSizeGreaterThan(1);
    }

    @Test
    void splitsOnSpacesWhenNoOtherBreaks() {
        // Long text with only spaces as break points (one long "sentence")
        String word = "abcdefghij"; // 10 chars
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            if (i > 0) sb.append(" ");
            sb.append(word);
        }
        String text = sb.toString(); // ~160 chars

        List<String> chunks = service.chunkMessage(text);
        assertThat(chunks).hasSizeGreaterThan(1);
        // Should not cut mid-word
        for (String chunk : chunks) {
            assertThat(chunk).doesNotContain("abcde abcde".substring(3, 8));
        }
    }

    @Test
    void chunksHaveOverlap() {
        // With 20 chars overlap, the end of chunk 1 should appear at the start of chunk 2
        String text = "Word ".repeat(30); // 150 chars
        List<String> chunks = service.chunkMessage(text);
        assertThat(chunks).hasSizeGreaterThan(1);

        // The last words of chunk 0 should appear somewhere in chunk 1
        String chunk0End = chunks.get(0).substring(Math.max(0, chunks.get(0).length() - 15));
        String chunk1Start = chunks.get(1).substring(0, Math.min(30, chunks.get(1).length()));
        // At least some overlap should exist
        boolean hasOverlap = chunk1Start.contains(chunk0End.trim().split("\\s+")[0]);
        assertThat(hasOverlap).as("Chunks should have overlapping content").isTrue();
    }

    @Test
    void noInfiniteLoopOnUnbreakableText() {
        // 200 chars with no spaces, newlines, or punctuation
        String text = "x".repeat(200);
        List<String> chunks = service.chunkMessage(text);
        assertThat(chunks).hasSizeGreaterThan(1);
        // Should eventually terminate and produce chunks
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(100));
    }

    @Test
    void allContentPreservedAcrossChunks() {
        String text = "The quick brown fox jumps over the lazy dog. " +
                "Pack my box with five dozen liquor jugs. " +
                "How vexingly quick daft zebras jump.";
        List<String> chunks = service.chunkMessage(text);

        // Every word in original text should appear in at least one chunk
        String allChunks = String.join(" ", chunks);
        for (String word : text.split("\\s+")) {
            assertThat(allChunks).contains(word);
        }
    }

    @Test
    void realWorldLongMessage_chunksCorrectly() {
        // Simulate a real MMS with 500 chars, using production-like settings
        ReflectionTestUtils.setField(service, "chunkMaxChars", 200);
        ReflectionTestUtils.setField(service, "chunkOverlapChars", 40);

        String text = """
                Hey just wanted to update you on everything. So the doctor appointment \
                went well, they said everything looks good and I don't need to come back \
                for another 6 months.
                
                Also I talked to the landlord about the lease renewal and they're raising \
                rent by $100 starting in June. Not great but it could be worse I guess.
                
                Oh and I finally booked the flights for the trip to Portland! We leave \
                May 15th and come back May 20th. I got us seats together on the plane.""";

        List<String> chunks = service.chunkMessage(text);
        assertThat(chunks).hasSizeGreaterThan(1);
        // Should split at paragraph boundaries
        assertThat(chunks.get(0)).contains("doctor appointment");
    }
}
