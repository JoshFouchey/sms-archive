package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ImportDirectoryWatcher covering core new functionality:
 * - Scanning and processing files (delete vs retain mode)
 * - File age threshold enforcement
 * - Pause / resume logic
 */
class ImportDirectoryWatcherTest {

    private ImportService importService;
    private UserRepository userRepository;
    private ImportDirectoryWatcher watcher;
    private Path tempRoot;

    @BeforeEach
    void setup() throws IOException {
        importService = Mockito.mock(ImportService.class);
        userRepository = Mockito.mock(UserRepository.class);
        watcher = new ImportDirectoryWatcher(importService, userRepository);
        tempRoot = Files.createTempDirectory("import-drop-test");

        // Inject @Value fields via reflection (since we are unit-testing the bean logic directly)
        setField("importDirectoryPath", tempRoot.toString());
        setField("scanIntervalSeconds", 1L); // not used directly in unit test
        setField("fileAgeThresholdSeconds", 1L); // keep low for test
        setField("retentionDays", 1); // for completeness
    }

    private void setField(String name, Object value) {
        try {
            var f = ImportDirectoryWatcher.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(watcher, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private User makeUser(String username) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername(username);
        return u;
    }

    /** Stub the import to finish immediately with the given outcome, invoking the disposal callback. */
    @SuppressWarnings("unchecked")
    private void stubImportFinishing(String username, boolean success) throws Exception {
        when(importService.startImportAsyncForUser(any(Path.class), eq(username), any(BiConsumer.class)))
                .thenAnswer(inv -> {
                    UUID jobId = UUID.randomUUID();
                    BiConsumer<UUID, Boolean> onFinished = inv.getArgument(2);
                    onFinished.accept(jobId, success);
                    return jobId;
                });
    }

    @Test
    @DisplayName("Processes eligible XML file and deletes it in deleteAfterImport mode")
    void processesAndDeletesFile() throws Exception {
        setField("deleteAfterImport", true);
        watcher.init();

        String username = "alice";
        Path userDir = tempRoot.resolve(username);
        Files.createDirectories(userDir);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(makeUser(username)));
        stubImportFinishing(username, true);

        Path xml = userDir.resolve("backup.xml");
        Files.writeString(xml, "<messages></messages>");
        // Backdate creation time to satisfy age threshold
        Files.setAttribute(xml, "basic:creationTime", FileTime.from(Instant.now().minusSeconds(5)));

        watcher.scanImportDirectory();

        // Verify import triggered
        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(importService).startImportAsyncForUser(pathCaptor.capture(), eq(username), any());
        assertThat(pathCaptor.getValue().getFileName().toString()).isEqualTo("backup.xml");
        // File should be deleted
        assertThat(Files.exists(xml)).isFalse();
    }

    @Test
    @DisplayName("Processes eligible XML file and moves it to processed directory when retention enabled")
    void processesAndRetainsFile() throws Exception {
        setField("deleteAfterImport", false);
        watcher.init();

        String username = "bob";
        Path userDir = tempRoot.resolve(username);
        Files.createDirectories(userDir);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(makeUser(username)));
        stubImportFinishing(username, true);

        Path xml = userDir.resolve("conversation.xml");
        Files.writeString(xml, "<messages></messages>");
        Files.setAttribute(xml, "basic:creationTime", FileTime.from(Instant.now().minusSeconds(5)));

        watcher.scanImportDirectory();

        // Verify import triggered
        verify(importService).startImportAsyncForUser(any(Path.class), eq(username), any());

        Path processedDir = userDir.resolve("processed");
        assertThat(Files.exists(processedDir)).isTrue();
        // Expect file moved
        assertThat(Files.list(processedDir)
                .filter(p -> p.getFileName().toString().startsWith("conversation"))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Skips file younger than age threshold")
    void skipsYoungFile() throws Exception {
        setField("deleteAfterImport", true);
        setField("fileAgeThresholdSeconds", 30L); // raise threshold
        watcher.init();

        String username = "charlie";
        Path userDir = tempRoot.resolve(username);
        Files.createDirectories(userDir);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(makeUser(username)));

        Path xml = userDir.resolve("recent.xml");
        Files.writeString(xml, "<messages></messages>");
        // creationTime is NOW (not old enough)
        watcher.scanImportDirectory();

        verify(importService, never()).startImportAsyncForUser(any(Path.class), eq(username), any());
        assertThat(Files.exists(xml)).isTrue(); // still there
    }

    @Test
    @DisplayName("Pause prevents scanning; resume re-enables it")
    void pauseResume() throws Exception {
        setField("deleteAfterImport", true);
        watcher.init();
        String username = "dana";
        Path userDir = tempRoot.resolve(username);
        Files.createDirectories(userDir);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(makeUser(username)));

        Path xml = userDir.resolve("file.xml");
        Files.writeString(xml, "<messages></messages>");
        Files.setAttribute(xml, "basic:creationTime", FileTime.from(Instant.now().minusSeconds(5)));

        watcher.pauseScanning();
        watcher.scanImportDirectory();
        verify(importService, never()).startImportAsyncForUser(any(Path.class), eq(username), any());

        watcher.resumeScanning();
        watcher.scanImportDirectory();
        verify(importService, times(1)).startImportAsyncForUser(any(Path.class), eq(username), any());
    }

    @Test
    @DisplayName("Failed import moves file to failed directory instead of processed")
    void failedImportMovesToFailedDirectory() throws Exception {
        setField("deleteAfterImport", true); // even in delete mode, a failed file must be kept
        watcher.init();

        String username = "erin";
        Path userDir = tempRoot.resolve(username);
        Files.createDirectories(userDir);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(makeUser(username)));
        stubImportFinishing(username, false);

        Path xml = userDir.resolve("bad-backup.xml");
        Files.writeString(xml, "<messages></messages>");
        Files.setAttribute(xml, "basic:creationTime", FileTime.from(Instant.now().minusSeconds(5)));

        watcher.scanImportDirectory();

        assertThat(Files.exists(xml)).isFalse();
        Path failedDir = userDir.resolve("failed");
        assertThat(Files.exists(failedDir)).isTrue();
        assertThat(Files.list(failedDir)
                .filter(p -> p.getFileName().toString().startsWith("bad-backup"))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("File is not deleted or moved until the import job completes")
    @SuppressWarnings("unchecked")
    void fileStaysInPlaceUntilImportCompletes() throws Exception {
        setField("deleteAfterImport", true);
        watcher.init();

        String username = "frank";
        Path userDir = tempRoot.resolve(username);
        Files.createDirectories(userDir);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(makeUser(username)));

        // Capture the callback without invoking it - simulates a still-running async job
        ArgumentCaptor<BiConsumer<UUID, Boolean>> callbackCaptor = ArgumentCaptor.forClass(BiConsumer.class);
        when(importService.startImportAsyncForUser(any(Path.class), eq(username), any(BiConsumer.class)))
                .thenReturn(UUID.randomUUID());

        Path xml = userDir.resolve("large-backup.xml");
        Files.writeString(xml, "<messages></messages>");
        Files.setAttribute(xml, "basic:creationTime", FileTime.from(Instant.now().minusSeconds(5)));

        watcher.scanImportDirectory();
        verify(importService).startImportAsyncForUser(any(Path.class), eq(username), callbackCaptor.capture());

        // Job still running: the file must remain readable for the worker thread
        assertThat(Files.exists(xml)).isTrue();

        // A rescan must not re-process the in-flight file
        watcher.scanImportDirectory();
        verify(importService, times(1)).startImportAsyncForUser(any(Path.class), eq(username), any());

        // Job finishes: now the file gets disposed
        callbackCaptor.getValue().accept(UUID.randomUUID(), true);
        assertThat(Files.exists(xml)).isFalse();
    }
}

