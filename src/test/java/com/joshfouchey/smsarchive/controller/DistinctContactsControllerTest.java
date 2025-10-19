package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.ContactDto;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class DistinctContactsControllerTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ContactRepository contactRepository;

    @BeforeEach
    void setUp() {
        contactRepository.deleteAll();
        contactRepository.saveAll(Arrays.asList(
                Contact.builder().name("Alice").number("+1 (555) 111-2222").normalizedNumber("15551112222").build(),
                Contact.builder().name("Bob").number("+1 (555) 333-4444").normalizedNumber("15553334444").build(),
                Contact.builder().name(null).number("+1 (555) 000-9999").normalizedNumber("15550009999").build()
        ));
    }

    @Test
    void getAllContacts_returnsSortedDistinctContacts() {
        ResponseEntity<ContactDto[]> resp = restTemplate.getForEntity("/api/contacts", ContactDto[].class);
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

