package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ImportDirectoryWatcherRetentionCleanupTest {

    private ImportService importService; // not used directly
    private UserRepository userRepository;
    private ImportDirectoryWatcher watcher;
    private Path tempRoot;

    @BeforeEach
    void setup() throws Exception {
        importService = Mockito.mock(ImportService.class);
        userRepository = Mockito.mock(UserRepository.class);
        watcher = new ImportDirectoryWatcher(importService, userRepository);
        tempRoot = Files.createTempDirectory("retention-test");
        setField("importDirectoryPath", tempRoot.toString());
        setField("deleteAfterImport", false); // retention mode
        setField("retentionDays", 1); // cleanup threshold
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

    @Test
    @DisplayName("Old processed files are deleted by cleanup job")
    void deletesOldProcessedFiles() throws Exception {
        watcher.init();
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername("alex");
        when(userRepository.findByUsername("alex")).thenReturn(Optional.of(u));

        Path userDir = tempRoot.resolve("alex");
        Path processedDir = userDir.resolve("processed");
        Files.createDirectories(processedDir);

        // Create two files: one old, one recent
        Path oldFile = processedDir.resolve("old.xml");
        Path newFile = processedDir.resolve("new.xml");
        Files.writeString(oldFile, "<messages/>\n");
        Files.writeString(newFile, "<messages/>\n");
        // Backdate last modified for old file beyond retentionDays
        Files.setLastModifiedTime(oldFile, FileTime.from(Instant.now().minusSeconds(2 * 24 * 3600))); // 2 days

        // Run cleanup with retentionDays=1 (delete files older than 1 day)
        watcher.cleanupOldProcessedFiles();

        assertThat(Files.exists(oldFile)).isFalse();
        assertThat(Files.exists(newFile)).isTrue();
    }
}

