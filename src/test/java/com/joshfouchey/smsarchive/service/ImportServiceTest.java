// ImportServiceTest.java
package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ImportServiceTest {

    @MockitoBean
    private MessageRepository messageRepo;

    private ImportService importService;

    @BeforeEach
    void setup() {
        importService = new ImportService(messageRepo);
    }

    @Test
    void testImportFromXml() throws Exception {
        File xmlFile = new File("src/test/resources/test-messages.xml");
        assertThat(xmlFile).exists();

        int count = importService.importFromXml(xmlFile);
        assertThat(count).isGreaterThan(0);

        // Capture saved messages
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageRepo, times(1)).saveAll(captor.capture());

        List<Message> savedMessages = captor.getValue();
        assertThat(savedMessages).isNotEmpty();

        // Example: validate first message
        Message first = savedMessages.get(0);
        assertThat(first.getProtocol()).isIn("SMS", "MMS", "RCS");
        assertThat(first.getTimestamp()).isNotNull();

        if ("SMS".equals(first.getProtocol())) {
            assertThat(first.getBody()).isNotBlank();
        }

        if ("MMS".equals(first.getProtocol()) || "RCS".equals(first.getProtocol())) {
            List<MessagePart> parts = first.getParts();
            assertThat(parts).isNotEmpty();

            // If it has media, verify the file was written
            parts.stream()
                    .filter(p -> p.getFilePath() != null)
                    .forEach(p -> {
                        Path path = Path.of(p.getFilePath());
                        assertThat(Files.exists(path)).isTrue();
                        assertThat(path.getFileName().toString()).startsWith("part");
                    });
        }
    }
}
