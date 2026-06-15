package com.joshfouchey.smsarchive.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class TextToSqlService {

    private static final int MAX_ROWS = 50;
    private static final int QUERY_TIMEOUT_SECONDS = 10;

    private static final Pattern DANGEROUS_SQL = Pattern.compile(
            "\\b(INSERT\\s+INTO|UPDATE\\s+\\w|DELETE\\s+FROM|DROP\\s|ALTER\\s|CREATE\\s|TRUNCATE\\s|" +
            "GRANT\\s|REVOKE\\s|EXECUTE\\s|EXEC\\s|CALL\\s|" +
            "MERGE\\s|COPY\\s|VACUUM\\s|REINDEX\\s|COMMENT\\s+ON|SECURITY\\s|OWNER\\s|pg_sleep)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SELECT_ONLY = Pattern.compile(
            "^\\s*(SELECT|WITH)\\b", Pattern.CASE_INSENSITIVE);

    private static final String SCHEMA_PROMPT = """
            Write a PostgreSQL SELECT query. Output ONLY the SQL, nothing else.

            Tables:
            - messages: id, user_id (UUID), sender_contact_id, conversation_id, timestamp, body, direction ('INBOUND'/'OUTBOUND'), protocol ('SMS'/'MMS'/'RCS')
            - contacts: id, user_id (UUID), number, normalized_number, name
            - conversations: id, user_id (UUID), name, last_message_at
            - conversation_contacts: conversation_id, contact_id
            - message_parts: id, message_id, ct (MIME type), name, file_path, size_bytes

            Always filter by user_id = '__USER_ID__'. Max %d rows.
            "texts" means rows in the messages table.
            To find messages with a contact, match contacts.name using ILIKE.
            Do NOT filter by protocol unless the question specifically asks about SMS, MMS, or RCS.

            Question: %s""";

    private final ChatModel chatModel;
    private final JdbcTemplate jdbcTemplate;

    @Value("${smsarchive.ai.sql.model:qwen2.5-coder-3b-instruct}")
    private String sqlModelName;

    public TextToSqlService(ChatModel chatModel, JdbcTemplate jdbcTemplate) {
        this.chatModel = chatModel;
        this.jdbcTemplate = jdbcTemplate;
    }

    public TextToSqlResult generateAndExecute(String question, UUID userId) {
        GeneratedSql generated = generateSql(question);
        String sql = generated.sql();
        long generationMs = generated.generationMs();

        // If the SQL looks incomplete (no FROM clause), retry once
        if (!sql.toLowerCase().contains("from")) {
            log.info("Text-to-SQL looks incomplete (no FROM), retrying...");
            generated = generateSql(question);
            sql = generated.sql();
            generationMs += generated.generationMs();
        }

        if (!sql.toLowerCase().contains("from")) {
            throw new TextToSqlException("Generated SQL is incomplete (no FROM clause)", sql, null, null);
        }

        String safeSql = prepareSql(sql, userId);
        try {
            TimedRows result = executeSql(safeSql);
            String answer = formatAnswer(question, result.rows());
            return new TextToSqlResult(answer, sql, safeSql, result.rows(), generationMs, result.executionMs());
        } catch (TextToSqlException e) {
            // Retry once with a fresh generation if execution fails
            log.info("Text-to-SQL first attempt failed ({}), retrying...", e.getMessage());
            generated = generateSql(question);
            sql = generated.sql();
            generationMs += generated.generationMs();
            safeSql = prepareSql(sql, userId);
            TimedRows result = executeSql(safeSql);
            String answer = formatAnswer(question, result.rows());
            return new TextToSqlResult(answer, sql, safeSql, result.rows(), generationMs, result.executionMs());
        }
    }

    public TextToSqlResult executeUserSql(String sql, UUID userId) {
        String safeSql = prepareSql(sql, userId);
        TimedRows result = executeSql(safeSql);
        String answer = formatAnswer("Edited SQL", result.rows());
        return new TextToSqlResult(answer, sql, safeSql, result.rows(), 0, result.executionMs());
    }

    private GeneratedSql generateSql(String question) {
        String normalizedQuestion = normalizeQuestion(question);
        String prompt = String.format(SCHEMA_PROMPT, MAX_ROWS, normalizedQuestion);

        try {
            long start = System.currentTimeMillis();
            ChatResponse response = chatModel.call(
                    new Prompt(prompt, OpenAiChatOptions.builder()
                            .model(sqlModelName)
                            .temperature(0.1)
                            .maxTokens(512)
                            .frequencyPenalty(1.2)
                            .build()));

            String raw = response.getResult().getOutput().getText().trim();
            raw = extractSql(raw);
            log.info("Text-to-SQL generated: {}", raw.replace("\n", " "));
            return new GeneratedSql(raw, System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new TextToSqlException("Failed to generate SQL: " + e.getMessage(), e);
        }
    }

    String prepareSql(String sql, UUID userId) {
        String safeSql = injectUserId(sql, userId);

        // If the query is not already scoped to this exact user, add a defensive filter.
        if (!safeSql.contains(userId.toString())) {
            log.info("Text-to-SQL missing user_id, injecting filter");
            safeSql = injectUserIdFilter(safeSql, userId);
        }

        validateSql(safeSql);
        return safeSql;
    }

    /** Extract SQL from model output, handling cases where the model wraps it in prose. */
    private String extractSql(String raw) {
        // Strip markdown code fences if present
        raw = raw.replaceAll("(?s)^```(?:sql)?\\s*", "").replaceAll("(?s)\\s*```$", "").trim();

        // If the output starts with prose instead of SQL, try to find the SQL within it
        if (!raw.isEmpty() && !raw.toUpperCase().matches("^(SELECT|WITH)\\b.*")) {
            // Look for SELECT or WITH statement embedded in the prose
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?si)((?:WITH\\s+\\w+|SELECT)\\b.+)")
                    .matcher(raw);
            if (m.find()) {
                raw = m.group(1).trim();
            }
        }

        // Truncate at repetition loops (e.g. "with with with with...")
        raw = raw.replaceAll("(?i)(\\b\\w+\\b)(\\s+\\1){3,}.*", "").trim();

        // Remove trailing semicolons
        raw = raw.replaceAll(";\\s*$", "").trim();
        // Fix malformed CTE: "WITH (" → "WITH"
        raw = raw.replaceAll("(?i)^WITH\\s*\\(\\s*\\n?", "WITH ");
        // Fix empty COUNT(): COUNT() → COUNT(*)
        raw = raw.replaceAll("(?i)COUNT\\(\\)", "COUNT(*)");
        // Fix stray ") AS sub" or ") AS sub SELECT" between CTE and final SELECT
        raw = raw.replaceAll("(?i)\\)\\s*\\)\\s*AS\\s+\\w+\\s*SELECT", ") SELECT");

        return raw;
    }

    /** Normalize question text to reduce SLM confusion with certain phrasings. */
    private String normalizeQuestion(String question) {
        // "vs" / "vs." triggers bad CTE syntax in qwen2.5-coder — rephrase to "compared to"
        return question.replaceAll("(?i)\\bvs\\.?\\b", "compared to");
    }

    private String injectUserId(String sql, UUID userId) {
        // UUID.toString() is safe (hex + dashes only), no injection risk
        return sql.replace("'__USER_ID__'", "'" + userId.toString() + "'")
                  .replace("__USER_ID__", "'" + userId.toString() + "'");
    }

    /**
     * If the model forgot the user_id filter, inject it into the WHERE clause.
     * Finds the first WHERE and appends, or adds WHERE before ORDER BY/GROUP BY/LIMIT.
     */
    private String injectUserIdFilter(String sql, UUID userId) {
        String filter = "m.user_id = '" + userId.toString() + "'";
        String lower = sql.toLowerCase();

        if (lower.contains("where")) {
            // Append to existing WHERE
            int whereIdx = lower.indexOf("where") + 5;
            return sql.substring(0, whereIdx) + " " + filter + " AND" + sql.substring(whereIdx);
        }

        // No WHERE — insert before ORDER BY, GROUP BY, or LIMIT
        String[] insertBefore = {"order by", "group by", "limit"};
        for (String keyword : insertBefore) {
            int idx = lower.indexOf(keyword);
            if (idx > 0) {
                return sql.substring(0, idx) + "WHERE " + filter + " " + sql.substring(idx);
            }
        }

        // Append at end
        return sql + " WHERE " + filter;
    }

    void validateSql(String sql) {
        if (!SELECT_ONLY.matcher(sql).find()) {
            log.warn("Text-to-SQL validation failed: not a SELECT query. SQL: {}", sql);
            throw new TextToSqlException("Generated SQL is not a SELECT query", sql, null, null);
        }
        if (DANGEROUS_SQL.matcher(sql).find()) {
            log.warn("Text-to-SQL validation failed: prohibited keywords. SQL: {}", sql);
            throw new TextToSqlException("Generated SQL contains prohibited keywords", sql, null, null);
        }
        if (!sql.toLowerCase().contains("user_id")) {
            log.warn("Text-to-SQL validation failed: missing user_id filter. SQL: {}", sql);
            throw new TextToSqlException("Generated SQL missing user_id filter", sql, null, null);
        }
        // Reject multiple statements
        long semiCount = sql.chars().filter(c -> c == ';').count();
        if (semiCount > 0) {
            throw new TextToSqlException("Generated SQL contains multiple statements", sql, null, null);
        }
    }

    @SuppressWarnings("deprecation")
    private TimedRows executeSql(String sql) {
        long start = System.currentTimeMillis();
        try {
            jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            return new TimedRows(rows, System.currentTimeMillis() - start);
        } catch (Exception e) {
            // Surface the root PostgreSQL error, not just Spring's wrapper
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            String detail = root.getMessage() != null ? root.getMessage() : e.getMessage();
            log.error("Text-to-SQL execution failed. Detail: {}. SQL: {}", detail, sql);
            throw new TextToSqlException("SQL execution failed: " + detail, null, sql, detail, e);
        } finally {
            jdbcTemplate.setQueryTimeout(0);
        }
    }

    private String formatAnswer(String question, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "I ran a query but found no results for that question.";
        }

        // Single value result
        if (rows.size() == 1 && rows.get(0).size() == 1) {
            Object val = rows.get(0).values().iterator().next();
            String colName = rows.get(0).keySet().iterator().next()
                    .replace("_", " ");
            return String.format("The %s is %s.", colName, formatValue(val));
        }

        // Single row, multiple columns
        if (rows.size() == 1) {
            Map<String, Object> row = rows.get(0);
            String details = row.entrySet().stream()
                    .map(e -> e.getKey().replace("_", " ") + ": " + formatValue(e.getValue()))
                    .collect(Collectors.joining(", "));
            return details;
        }

        // Multiple rows — build a summary
        int count = rows.size();
        Set<String> cols = rows.get(0).keySet();

        // Detect name + count pattern (common for "top X" queries)
        String nameCol = cols.stream()
                .filter(c -> c.toLowerCase().contains("name") || c.toLowerCase().contains("contact"))
                .findFirst().orElse(null);
        String countCol = cols.stream()
                .filter(c -> c.toLowerCase().contains("count") || c.toLowerCase().contains("total")
                        || c.toLowerCase().contains("messages") || c.toLowerCase().contains("num"))
                .findFirst().orElse(null);

        if (nameCol != null && countCol != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Found %d results:\n\n", count));
            for (int i = 0; i < rows.size(); i++) {
                sb.append(String.format("%d. %s — %s\n",
                        i + 1,
                        formatValue(rows.get(i).get(nameCol)),
                        formatValue(rows.get(i).get(countCol))));
            }
            return sb.toString();
        }

        return String.format("Found %d results. See the table below for details.", count);
    }

    private String formatValue(Object val) {
        if (val == null) return "—";
        if (val instanceof Number num) {
            if (num instanceof Long || num instanceof Integer) {
                return String.format("%,d", num.longValue());
            }
            return String.format("%.2f", num.doubleValue());
        }
        return val.toString();
    }

    public record TextToSqlResult(
            String answer,
            String generatedSql,
            String executedSql,
            List<Map<String, Object>> rows,
            long generationMs,
            long executionMs
    ) {}

    private record GeneratedSql(String sql, long generationMs) {}

    private record TimedRows(List<Map<String, Object>> rows, long executionMs) {}

    public static class TextToSqlException extends RuntimeException {
        private final String generatedSql;
        private final String executedSql;
        private final String dbError;

        public TextToSqlException(String message) {
            this(message, null, null, null);
        }
        public TextToSqlException(String message, Throwable cause) {
            this(message, null, null, null, cause);
        }
        public TextToSqlException(String message, String generatedSql, String executedSql, String dbError) {
            super(message);
            this.generatedSql = generatedSql;
            this.executedSql = executedSql;
            this.dbError = dbError;
        }
        public TextToSqlException(String message, String generatedSql, String executedSql, String dbError, Throwable cause) {
            super(message, cause);
            this.generatedSql = generatedSql;
            this.executedSql = executedSql;
            this.dbError = dbError;
        }

        public String getGeneratedSql() { return generatedSql; }
        public String getExecutedSql() { return executedSql; }
        public String getDbError() { return dbError; }
    }
}
