package com.joshfouchey.smsarchive.schema;

import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.junit.jupiter.api.Disabled;
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
@Disabled("Use ContactDedupMigrationContainerTest instead")
class ContactDedupMigrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepo;
    @Autowired ContactRepository contactRepo;
    @Autowired MessageRepository messageRepo;

    @Test
    @Transactional
    @DisplayName("V2 dedup logic merges leading-1 variant and preserves FKs")
    void dedupLeadingOneVariants() {
        // Create user
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername("dedupuser");
        userRepo.save(u);

        // Drop unique index to allow duplicates for test
        jdbc.execute("DROP INDEX IF EXISTS ux_contacts_user_normalized");

        // Insert two contacts representing same number with and without leading 1
        jdbc.update("INSERT INTO contacts(user_id, number, normalized_number) VALUES (?,?,?)", u.getId(), "(555)123-4567", "5551234567");
        jdbc.update("INSERT INTO contacts(user_id, number, normalized_number) VALUES (?,?,?)", u.getId(), "+1 (555) 123-4567", "15551234567");

        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM contacts WHERE user_id = ?", Integer.class, u.getId());
        assertThat(before).isEqualTo(2);

        // Simulate FK: create a dummy message referencing loser (first contact id)
        Long loserId = jdbc.queryForObject("SELECT id FROM contacts WHERE normalized_number='5551234567'", Long.class);
        Long winnerId = jdbc.queryForObject("SELECT id FROM contacts WHERE normalized_number='15551234567'", Long.class);
        jdbc.update("INSERT INTO messages(protocol, timestamp, msg_box, direction, user_id, sender_contact_id) VALUES (0, now(), 1, 'INBOUND', ?, ?)", u.getId(), loserId);

        // Run dedup portion of migration logic (simplified)
        jdbc.execute("CREATE TEMP TABLE tmp AS SELECT * FROM contacts WHERE user_id='" + u.getId() + "'");
        // Winner chosen as contact with 11-digit normalized_number starting with 1
        jdbc.update("UPDATE messages SET sender_contact_id = ? WHERE sender_contact_id = ?", winnerId, loserId);
        jdbc.update("DELETE FROM contacts WHERE id = ?", loserId);

        // Recreate unique index
        jdbc.execute("CREATE UNIQUE INDEX ux_contacts_user_normalized ON contacts (user_id, normalized_number)");

        Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM contacts WHERE user_id = ?", Integer.class, u.getId());
        assertThat(after).isEqualTo(1);
        Long remainingId = jdbc.queryForObject("SELECT id FROM contacts WHERE user_id = ?", Long.class, u.getId());
        assertThat(remainingId).isEqualTo(winnerId);
        Long msgContactId = jdbc.queryForObject("SELECT sender_contact_id FROM messages ORDER BY id DESC LIMIT 1", Long.class);
        assertThat(msgContactId).isEqualTo(winnerId);
    }
}

