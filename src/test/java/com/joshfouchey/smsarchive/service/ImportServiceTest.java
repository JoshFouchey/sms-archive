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
}
