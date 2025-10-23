package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.dto.ContactDto;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DistinctContactsControllerTest extends EnhancedPostgresTestContainer {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ContactRepository contactRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private MessagePartRepository messagePartRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Clean in dependency order (parts -> messages -> contacts) to avoid FK & unique constraint issues if data left from previous tests.
        messagePartRepository.deleteAll();
        messageRepository.deleteAll();
        contactRepository.deleteAll();
        userRepository.deleteAll();

        // Use "test_fallback" as the username since TestOverridesConfig's CurrentUserProvider
        // falls back to this user when there's no authentication (which is the case with TestRestTemplate)
        testUser = new User();
        testUser.setUsername("test_fallback");
        testUser.setPasswordHash(passwordEncoder.encode("password"));
        testUser = userRepository.save(testUser);

        contactRepository.saveAll(Arrays.asList(
                Contact.builder().name("Alice").number("+1 (555) 111-2222").normalizedNumber("15551112222").user(testUser).build(),
                Contact.builder().name("Bob").number("+1 (555) 333-4444").normalizedNumber("15553334444").user(testUser).build(),
                Contact.builder().name(null).number("+1 (555) 000-9999").normalizedNumber("15550009999").user(testUser).build()
        ));
    }

    @Test
    void getAllContacts_returnsSortedDistinctContacts() {
        ResponseEntity<ContactDto[]> resp = restTemplate
                .getForEntity("/api/contacts", ContactDto[].class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        ContactDto[] body = resp.getBody();
        assertThat(body).isNotNull();
        List<ContactDto> contacts = Arrays.asList(body);
        assertThat(contacts).hasSize(3);
        assertThat(contacts).allSatisfy(c -> {
            assertThat(c.id()).isNotNull();
            assertThat(c.normalizedNumber()).isNotBlank();
        });
        assertThat(contacts.get(0).name()).isEqualTo("Alice");
        assertThat(contacts.get(1).name()).isEqualTo("Bob");
        assertThat(contacts.get(2).name()).isNull();
    }
}
