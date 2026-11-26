package com.joshfouchey.smsarchive.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class TextToSqlServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String SECRET = "SECRETNAME";

    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(TextToSqlService.class);
    private Level originalLevel;
    private JdbcTemplate jdbcTemplate;
    private TextToSqlService service;

    @BeforeEach
    void setUp() {
        originalLevel = serviceLogger.getLevel();
        serviceLogger.setLevel(Level.INFO); // production level
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new TextToSqlService(mock(ChatModel.class), jdbcTemplate, mock(Executor.class));
    }

    @AfterEach
    void restoreLogLevel() {
        serviceLogger.setLevel(originalLevel);
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

    @Test
    void validationFailures_logReasonButNotSql(CapturedOutput output) {
        assertThatThrownBy(() -> service.validateSql("DELETE FROM messages WHERE body = '" + SECRET + "' AND user_id = 'x'"))
                .isInstanceOf(TextToSqlService.TextToSqlException.class);
        assertThatThrownBy(() -> service.validateSql("SELECT pg_sleep(1) FROM messages WHERE body = '" + SECRET + "' AND user_id = 'x'"))
                .isInstanceOf(TextToSqlService.TextToSqlException.class);
        assertThatThrownBy(() -> service.validateSql("SELECT * FROM messages WHERE body = '" + SECRET + "'"))
                .isInstanceOf(TextToSqlService.TextToSqlException.class);

        assertThat(output.getAll())
                .contains("not a SELECT query", "prohibited keywords", "missing user_id filter")
                .doesNotContain(SECRET);
    }

    @Test
    void disallowedTable_logsTableButNotSql(CapturedOutput output) {
        assertThatThrownBy(() -> service.executeUserSql("SELECT * FROM users u WHERE u.username = '" + SECRET + "'", USER_ID))
                .isInstanceOf(TextToSqlService.TextToSqlException.class);

        assertThat(output.getAll()).contains("disallowed table: users").doesNotContain(SECRET);
    }

    @Test
    void executionFailure_logsDetailButNotSql(CapturedOutput output) {
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> service.executeUserSql(
                "SELECT COUNT(*) FROM messages m WHERE m.body ILIKE '%" + SECRET + "%'", USER_ID))
                .isInstanceOf(TextToSqlService.TextToSqlException.class);

        assertThat(output.getAll()).contains("execution failed. Detail: db down").doesNotContain(SECRET);
    }

    @Test
    void debugLevel_stillLogsSqlForTroubleshooting(CapturedOutput output) {
        serviceLogger.setLevel(Level.DEBUG);
        assertThatThrownBy(() -> service.validateSql("SELECT * FROM messages WHERE body = '" + SECRET + "'"))
                .isInstanceOf(TextToSqlService.TextToSqlException.class);

        assertThat(output.getAll()).contains(SECRET);
    }
}
