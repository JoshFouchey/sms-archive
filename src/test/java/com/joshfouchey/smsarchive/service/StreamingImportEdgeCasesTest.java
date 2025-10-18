package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class StreamingImportEdgeCasesTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", postgres::getJdbcUrl);
        reg.add("spring.datasource.username", postgres::getUsername);
        reg.add("spring.datasource.password", postgres::getPassword);
        reg.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired ImportService importService;
    @Autowired MessageRepository messageRepository;
    @Autowired MessagePartRepository messagePartRepository;

    private ImportService.ImportProgress run(PathWrapper p) throws Exception {
        UUID job = importService.startImportAsync(p.path());
        return importService.getProgress(job);
    }

    record PathWrapper(java.nio.file.Path path) {}

    @Test
    void emptyFileFailsGracefully() throws Exception {
        File f = Files.createTempFile("empty-import", ".xml").toFile(); // empty content
        ImportService.ImportProgress progress = run(new PathWrapper(f.toPath()));
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
        long beforeMessages = messageRepository.count();
        ImportService.ImportProgress progress = run(new PathWrapper(f.toPath()));
        assertThat(progress.getStatus()).isEqualTo("FAILED");
        assertThat(progress.getImportedMessages()).isZero();
        assertThat(messageRepository.count()).isEqualTo(beforeMessages);
        assertThat(progress.getError()).isNotBlank();
    }
}

