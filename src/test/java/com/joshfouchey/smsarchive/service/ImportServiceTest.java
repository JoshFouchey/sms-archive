package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.model.MessageProtocol;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ImportServiceTest {

    private MessageRepository messageRepo;
    private ContactRepository contactRepo;

    private ImportService importService;

    @BeforeEach
    void setup() {
        messageRepo = mock(MessageRepository.class);
        contactRepo = mock(ContactRepository.class);

        // Contact stubbing: always create/save a new contact when not found
        when(contactRepo.findByNormalizedNumber(anyString())).thenReturn(Optional.empty());
        when(contactRepo.save(any(Contact.class))).thenAnswer(inv -> {
            Contact c = inv.getArgument(0);
            if (c.getId() == null) c.setId(1L);
            return c;
        });

        importService = new ImportService(messageRepo, contactRepo);
    }

    @Test
    void testImportFromXml() throws Exception {
        File xmlFile = new File("src/test/resources/test-messages.xml");
        assertThat(xmlFile).exists();

        int count = importService.importFromXml(xmlFile);
        assertThat(count).isGreaterThan(0);

        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageRepo, times(1)).saveAll(captor.capture());

        List<Message> savedMessages = captor.getValue();
        assertThat(savedMessages).isNotEmpty();

        Message first = savedMessages.get(0);
        assertThat(first.getProtocol()).isIn(MessageProtocol.SMS, MessageProtocol.MMS, MessageProtocol.RCS);
        assertThat(first.getTimestamp()).isNotNull();
        assertThat(first.getContact()).isNotNull();

        if (first.getProtocol() == MessageProtocol.SMS) {
            assertThat(first.getBody()).isNotBlank();
        } else if (first.getProtocol() == MessageProtocol.MMS || first.getProtocol() == MessageProtocol.RCS) {
            List<MessagePart> parts = first.getParts();
            assertThat(parts).isNotEmpty();
            parts.stream()
                    .filter(p -> p.getFilePath() != null)
                    .forEach(p -> {
                        Path path = Path.of(p.getFilePath());
                        assertThat(Files.exists(path)).isTrue();
                        assertThat(path.getFileName().toString()).startsWith("part");
                    });
        }
    }

    @Test
    void testImportNullMediaAttributes() throws Exception {
        File xmlFile = new File("src/test/resources/test-messages-null-media.xml");
        assertThat(xmlFile).exists();

        int count = importService.importFromXml(xmlFile);
        assertThat(count).isEqualTo(1); // only one mms message

        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageRepo, times(1)).saveAll(captor.capture());

        List<Message> savedMessages = captor.getValue();
        assertThat(savedMessages).hasSize(1);
        Message mms = savedMessages.get(0);
        assertThat(mms.getProtocol()).isEqualTo(MessageProtocol.MMS);
        assertThat(mms.getParts()).hasSize(4); // all parts including text + smil + 2 media

        // Media summary map should exist
        Map<String, Object> media = mms.getMedia();
        assertThat(media).isNotNull();
        assertThat(media).containsKey("parts");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mediaParts = (List<Map<String, Object>>) media.get("parts");
        assertThat(mediaParts).hasSize(2); // exclude text/plain and application/smil

        // helper to find map by seq
        Map<String, Object> seq1 = mediaParts.stream()
                .filter(mp -> Objects.equals(mp.get("seq"), 1))
                .findFirst().orElseThrow();
        Map<String, Object> seq3 = mediaParts.stream()
                .filter(mp -> Objects.equals(mp.get("seq"), 3))
                .findFirst().orElseThrow();

        // Validate defaults for blank/missing ct and name
        assertThat(seq1.get("contentType")).isEqualTo("application/octet-stream");
        assertThat(seq1.get("name")).isEqualTo("");
        assertThat(seq1.get("filePath")).isInstanceOf(String.class);
        assertThat(Files.exists(Path.of(seq1.get("filePath").toString()))).isTrue();

        assertThat(seq3.get("contentType")).isEqualTo("application/octet-stream");
        assertThat(seq3.get("name")).isEqualTo("");
        assertThat(seq3.get("filePath")).isInstanceOf(String.class);
        assertThat(Files.exists(Path.of(seq3.get("filePath").toString()))).isTrue();

        // Ensure SMIL part was excluded
        assertThat(mediaParts.stream().anyMatch(mp -> Objects.equals(mp.get("seq"), 2))).isFalse();
    }
}
