package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ImportServiceDuplicateTest {

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

    @AfterEach
    void tearDown() {
        // Clean up between tests if future tests added (optional now)
    }

    private File createSampleXml() throws Exception {
        String xml = """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <messages exported_at=\"2025-01-01T12:00:00Z\">
                  <sms protocol=\"0\" address=\"+15551234567\" date=\"1696200000000\" type=\"1\" body=\"Hey there, long time no talk!\" contact_name=\"Alice Smith\"/>
                  <sms protocol=\"0\" address=\"+15551234567\" date=\"1696203600000\" type=\"2\" body=\"Hi Alice! Great to hear from you.\" contact_name=\"Alice Smith\"/>
                  <mms date=\"1696210000000\" msg_box=\"1\" address=\"+15557654321\" contact_name=\"Bob Jones\">
                    <parts>
                      <part seq=\"0\" ct=\"text/plain\" text=\"Here is the photo I mentioned.\"/>
                      <part seq=\"1\" ct=\"image/jpeg\" name=\"IMG_001.jpg\" data=\"/9j/4AAQSkZJRgABAQEASABIAAD...\"/>
                    </parts>
                    <addrs>
                      <addr type=\"137\" address=\"+15557654321\"/>
                      <addr type=\"151\" address=\"me\"/>
                    </addrs>
                  </mms>
                  <mms date=\"1696213600000\" msg_box=\"2\" address=\"+15557654321\" contact_name=\"Bob Jones\">
                    <parts>
                      <part seq=\"0\" ct=\"text/plain\" text=\"Got it, sending one back!\"/>
                      <part seq=\"1\" ct=\"image/png\" name=\"screenshot.png\" data=\"iVBORw0KGgoAAAANSUhEUgAA...\"/>
                    </parts>
                    <addrs>
                      <addr type=\"137\" address=\"me\"/>
                      <addr type=\"151\" address=\"+15557654321\"/>
                    </addrs>
                  </mms>
                </messages>
                """;
        File f = Files.createTempFile("import-test", ".xml").toFile();
        try (FileWriter fw = new FileWriter(f)) { fw.write(xml); }
        return f;
    }

    @Test
    void duplicateImportSkipsMessages() throws Exception {
        File xml = createSampleXml();

        long before = messageRepository.count();
        long partsBefore = messagePartRepository.count();

        int firstImported = importService.importFromXml(xml);
        long afterFirst = messageRepository.count();
        long partsAfterFirst = messagePartRepository.count();

        assertThat(firstImported).isGreaterThan(0);
        assertThat(afterFirst - before).isEqualTo(firstImported);

        // Second import (same file)
        int secondImported = importService.importFromXml(xml);
        long afterSecond = messageRepository.count();
        long partsAfterSecond = messagePartRepository.count();

        assertThat(secondImported).isZero();
        assertThat(afterSecond).isEqualTo(afterFirst);
        assertThat(partsAfterSecond).isEqualTo(partsAfterFirst);
    }
}
