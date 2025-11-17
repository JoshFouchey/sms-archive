package com.joshfouchey.smsarchive.schema;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ContactDedupMigrationContainerTest extends EnhancedPostgresTestContainer {

    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;

    @Test
    @Transactional
    @DisplayName("Dedup merges leading-1 variant and preserves message FK (container)")
    void dedupLeadingOneVariantsContainer() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername("dedupuser");
        u.setPasswordHash("x");
        userRepository.save(u);
        entityManager.flush(); // Ensure user is in DB before JDBC ops

        jdbc.execute("DROP INDEX IF EXISTS ux_contacts_user_normalized");
        jdbc.update("INSERT INTO contacts(user_id, number, normalized_number) VALUES (?,?,?)", u.getId(), "(555)123-4567", "5551234567");
        jdbc.update("INSERT INTO contacts(user_id, number, normalized_number) VALUES (?,?,?)", u.getId(), "+1 (555) 123-4567", "15551234567");

        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM contacts WHERE user_id = ?", Integer.class, u.getId());
        assertThat(before).isEqualTo(2);

        Long loserId = jdbc.queryForObject("SELECT id FROM contacts WHERE user_id = ? AND normalized_number='5551234567'", Long.class, u.getId());
        Long winnerId = jdbc.queryForObject("SELECT id FROM contacts WHERE user_id = ? AND normalized_number='15551234567'", Long.class, u.getId());
        jdbc.update("INSERT INTO messages(protocol, timestamp, msg_box, direction, user_id, sender_contact_id) VALUES ('SMS', now(), 1, 'INBOUND', ?, ?)", u.getId(), loserId);

        jdbc.update("UPDATE messages SET sender_contact_id = ? WHERE sender_contact_id = ?", winnerId, loserId);
        jdbc.update("DELETE FROM contacts WHERE id = ?", loserId);
        jdbc.execute("CREATE UNIQUE INDEX ux_contacts_user_normalized ON contacts (user_id, normalized_number)");

        Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM contacts WHERE user_id = ?", Integer.class, u.getId());
        assertThat(after).isEqualTo(1);
        Long remainingId = jdbc.queryForObject("SELECT id FROM contacts WHERE user_id = ?", Long.class, u.getId());
        assertThat(remainingId).isEqualTo(winnerId);
        Long msgContactId = jdbc.queryForObject("SELECT sender_contact_id FROM messages ORDER BY id DESC LIMIT 1", Long.class);
        assertThat(msgContactId).isEqualTo(winnerId);
    }
}

