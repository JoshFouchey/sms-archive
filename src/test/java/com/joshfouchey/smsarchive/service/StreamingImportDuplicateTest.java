package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class StreamingImportDuplicateTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", postgres::getJdbcUrl);
        reg.add("spring.datasource.username", postgres::getUsername);
        reg.add("spring.datasource.password", postgres::getPassword);
        reg.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    ImportService importService;
    @Autowired
    MessageRepository messageRepository;
    @Autowired
    MessagePartRepository messagePartRepository;

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

        // First import
        UUID firstJob = importService.startImportAsync(xml.toPath());
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(importService.getProgress(firstJob)).isNotNull();
            assertThat(importService.getProgress(firstJob).getStatus()).isEqualTo("COMPLETED");
        });
        ImportService.ImportProgress first = importService.getProgress(firstJob);
        long importedFirst = first.getImportedMessages();
        long duplicatesFirst = first.getDuplicateMessagesFinal();
        long processedFirst = first.getProcessedMessages();

        // Expect 4 processed (2 sms + 2 mms), 2 imported (one unique sms + one unique mms), 2 duplicates
        assertThat(processedFirst).withFailMessage("Expected 4 processed but got %d", processedFirst).isEqualTo(4);
        assertThat(importedFirst).withFailMessage("Expected 2 imported but got %d", importedFirst).isEqualTo(2);
        assertThat(duplicatesFirst).withFailMessage("Expected 2 duplicates but got %d", duplicatesFirst).isEqualTo(2);

        long afterFirstMessages = messageRepository.count();
        long afterFirstParts = messagePartRepository.count();
        assertThat(afterFirstMessages - beforeMessages).isEqualTo(importedFirst);
        assertThat(afterFirstParts - beforeParts).isGreaterThanOrEqualTo(1);

        // Second import (all should be duplicates now)
        UUID secondJob = importService.startImportAsync(xml.toPath());
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(importService.getProgress(secondJob)).isNotNull();
            assertThat(importService.getProgress(secondJob).getStatus()).isEqualTo("COMPLETED");
        });
        ImportService.ImportProgress second = importService.getProgress(secondJob);
        long importedSecond = second.getImportedMessages();
        long duplicatesSecond = second.getDuplicateMessagesFinal();
        long processedSecond = second.getProcessedMessages();

        assertThat(processedSecond).withFailMessage("Second run expected 4 processed but got %d", processedSecond).isEqualTo(4);
        assertThat(importedSecond).withFailMessage("Second run expected 0 imported but got %d", importedSecond).isZero();
        assertThat(duplicatesSecond).withFailMessage("Second run expected 4 duplicates but got %d", duplicatesSecond).isEqualTo(4);

        long afterSecondMessages = messageRepository.count();
        long afterSecondParts = messagePartRepository.count();
        assertThat(afterSecondMessages).isEqualTo(afterFirstMessages);
        assertThat(afterSecondParts).isEqualTo(afterFirstParts);
    }
}
