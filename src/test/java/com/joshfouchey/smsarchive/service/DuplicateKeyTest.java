package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessageDirection;
import com.joshfouchey.smsarchive.model.MessageProtocol;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DuplicateKeyTest {

    @Autowired
    ImportService importService;
    @Autowired
    ContactRepository contactRepository;

    @Test
    void duplicateKeyTrimsBodyAndUsesContactIdOrNull() {
        Contact contact = contactRepository.save(Contact.builder().number("+15551234567").normalizedNumber("5551234567").name("Alice").build());
        Message msg = new Message();
        msg.setContact(contact);
        msg.setBody("  Hello World  ");
        msg.setTimestamp(Instant.ofEpochMilli(123456789L));
        msg.setMsgBox(1);
        msg.setDirection(MessageDirection.INBOUND);
        msg.setProtocol(MessageProtocol.SMS);
        String key = importService.computeDuplicateKeyForTest(msg);
        assertThat(key)
            .startsWith(contact.getId().toString() + "|")
            .contains("Hello World")
            .endsWith("Hello World");
        Message msg2 = new Message();
        msg2.setContact(contact);
        msg2.setBody(null);
        msg2.setTimestamp(Instant.ofEpochMilli(123456789L));
        msg2.setMsgBox(1);
        msg2.setDirection(MessageDirection.INBOUND);
        msg2.setProtocol(MessageProtocol.SMS);
        String key2 = importService.computeDuplicateKeyForTest(msg2);
        assertThat(key2).endsWith("|");
        Message msg3 = new Message();
        msg3.setContact(null);
        msg3.setBody("Test");
        msg3.setTimestamp(Instant.ofEpochMilli(123456789L));
        msg3.setMsgBox(1);
        msg3.setDirection(MessageDirection.INBOUND);
        msg3.setProtocol(MessageProtocol.SMS);
        String key3 = importService.computeDuplicateKeyForTest(msg3);
        assertThat(key3).startsWith("null|");
    }
}
