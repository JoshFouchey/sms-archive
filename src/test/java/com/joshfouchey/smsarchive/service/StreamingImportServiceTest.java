package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StreamingImportServiceTest {

    private MessageRepository messageRepo;
    private ContactRepository contactRepo;
    private ImportService importService;

    private final List<List<Message>> savedBatches = new ArrayList<>();

    @BeforeEach
    void setup() {
        messageRepo = mock(MessageRepository.class);
        contactRepo = mock(ContactRepository.class);
        when(contactRepo.findByNormalizedNumber(anyString())).thenReturn(Optional.empty());
        when(contactRepo.save(any(Contact.class))).thenAnswer(inv -> {
            Contact c = inv.getArgument(0);
            if (c.getId() == null) c.setId(1L);
            return c;
        });
        // Duplicate check: always false
        when(messageRepo.existsDuplicate(any(), any(), anyInt(), any(), any())).thenReturn(false);
        // Capture saveAll batches
        doAnswer(inv -> {
            @SuppressWarnings("unchecked") List<Message> batch = (List<Message>) inv.getArgument(0);
            savedBatches.add(new ArrayList<>(batch));
            return null;
        }).when(messageRepo).saveAll(anyList());

        importService = new ImportService(messageRepo, contactRepo); // fallback thread execution
    }

    @Test
    void streamingImportProcessesAllMessagesAndTracksProgress() throws Exception {
        Path xml = Path.of("src/test/resources/test-streaming-large.xml");
        assertThat(xml).exists();

        var jobId = importService.startImportAsync(xml);
        assertThat(jobId).isNotNull();

        // Poll progress until completed or timeout
        long start = System.currentTimeMillis();
        ImportService.ImportProgress progress = null;
        while (System.currentTimeMillis() - start < 5000) { // 5s timeout
            progress = importService.getProgress(jobId);
            if (progress != null && ("COMPLETED".equals(progress.getStatus()) || "FAILED".equals(progress.getStatus()))) {
                break;
            }
            Thread.sleep(50);
        }
        assertThat(progress).isNotNull();
        assertThat(progress.getStatus()).isEqualTo("COMPLETED");
        assertThat(progress.getImportedMessages()).isEqualTo(5); // 5 messages in file
        assertThat(progress.getProcessedMessages()).isEqualTo(5);
        assertThat(progress.getDuplicateMessages()).isEqualTo(0);
        assertThat(progress.getPercentBytes()).isGreaterThan(0.0);

        // Ensure at least one batch persisted
        assertThat(savedBatches).isNotEmpty();
        int totalPersisted = savedBatches.stream().mapToInt(List::size).sum();
        assertThat(totalPersisted).isEqualTo(5);
    }
}

