package com.joshfouchey.smsarchive.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TextToSqlServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private TextToSqlService service;

    @BeforeEach
    void setUp() {
        service = new TextToSqlService(mock(ChatModel.class), mock(JdbcTemplate.class), mock(Executor.class));
    }

    @Test
    void prepareSql_replacesUserIdPlaceholder() {
        String prepared = service.prepareSql(
                "SELECT COUNT(*) FROM messages m WHERE m.user_id = '__USER_ID__'",
                USER_ID);

        assertThat(prepared).contains("m.user_id = '" + USER_ID + "'");
        assertThat(prepared).doesNotContain("__USER_ID__");
    }

    @Test
    void prepareSql_injectsMissingUserFilter() {
        String prepared = service.prepareSql("SELECT COUNT(*) FROM messages m", USER_ID);

        assertThat(prepared).isEqualTo("SELECT COUNT(*) FROM messages m WHERE m.user_id = '" + USER_ID + "'");
    }

    @Test
    void validateSql_rejectsNonSelectStatements() {
        assertThatThrownBy(() -> service.validateSql("DELETE FROM messages WHERE user_id = 'x'"))
                .isInstanceOf(TextToSqlService.TextToSqlException.class)
                .hasMessageContaining("not a SELECT");
    }

    @Test
    void validateSql_rejectsProhibitedKeywordsInsideSelect() {
        assertThatThrownBy(() -> service.validateSql("SELECT pg_sleep(10) FROM messages WHERE user_id = 'x'"))
                .isInstanceOf(TextToSqlService.TextToSqlException.class)
                .hasMessageContaining("prohibited keywords");
    }

    @Test
    void validateSql_rejectsMultipleStatements() {
        assertThatThrownBy(() -> service.validateSql("SELECT * FROM messages WHERE user_id = 'x'; SELECT 1"))
                .isInstanceOf(TextToSqlService.TextToSqlException.class)
                .hasMessageContaining("multiple statements");
    }
}
