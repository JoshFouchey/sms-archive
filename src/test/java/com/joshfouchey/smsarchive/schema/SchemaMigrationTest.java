package com.joshfouchey.smsarchive.schema;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaMigrationTest extends EnhancedPostgresTestContainer {

    @Autowired JdbcTemplate jdbc;

    @Test
    void essentialIndexesConstraintsAndTriggersExist() {
        Set<String> indexes = jdbc.query("SELECT indexname FROM pg_indexes WHERE tablename='messages'", (rs, i) -> rs.getString(1))
                .stream().collect(Collectors.toSet());
        assertThat(indexes).contains("ux_messages_dedupe", "ix_messages_dedupe_prefix", "idx_messages_body_fts");

        Set<String> constraints = jdbc.query("SELECT conname FROM pg_constraint WHERE conrelid='messages'::regclass", (rs,i)->rs.getString(1))
                .stream().collect(Collectors.toSet());
        assertThat(constraints).contains("chk_messages_protocol", "chk_messages_direction");

        Set<String> triggers = jdbc.query("SELECT tgname FROM pg_trigger WHERE tgrelid='messages'::regclass AND NOT tgisinternal", (rs,i)->rs.getString(1))
                .stream().collect(Collectors.toSet());
        assertThat(triggers).contains("trg_messages_updated_at");
    }
}

