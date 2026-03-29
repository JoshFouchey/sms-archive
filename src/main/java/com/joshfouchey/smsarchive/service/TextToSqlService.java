package com.joshfouchey.smsarchive.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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
            To find messages with a contact, match contacts.name using ILIKE.

            Question: %s""";

    private final RestClient restClient;
    private final JdbcTemplate jdbcTemplate;

    @Value("${smsarchive.ai.sql.model:qwen2.5-coder:7b}")
    private String sqlModelName;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    public TextToSqlService(JdbcTemplate jdbcTemplate, RestClient.Builder restClientBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.restClient = restClientBuilder.build();
    }

    public TextToSqlResult generateAndExecute(String question, UUID userId) {
        String sql = generateSql(question);

        // If the SQL looks incomplete (no FROM clause), retry once
        if (!sql.toLowerCase().contains("from")) {
            log.info("Text-to-SQL looks incomplete (no FROM), retrying...");
            sql = generateSql(question);
        }

        if (!sql.toLowerCase().contains("from")) {
            throw new TextToSqlException("Generated SQL is incomplete (no FROM clause)");
        }

        String safeSql = injectUserId(sql, userId);

        // If model forgot user_id filter, inject it
        if (!safeSql.toLowerCase().contains("user_id")) {
            log.info("Text-to-SQL missing user_id, injecting filter");
            safeSql = injectUserIdFilter(safeSql, userId);
        }

        validateSql(safeSql);
        try {
            List<Map<String, Object>> rows = executeSql(safeSql);
            String answer = formatAnswer(question, rows);
            return new TextToSqlResult(answer, sql, rows);
        } catch (TextToSqlException e) {
            // Retry once with a fresh generation if execution fails
            log.info("Text-to-SQL first attempt failed ({}), retrying...", e.getMessage());
            sql = generateSql(question);
            safeSql = injectUserId(sql, userId);
            if (!safeSql.toLowerCase().contains("user_id")) {
                safeSql = injectUserIdFilter(safeSql, userId);
            }
            validateSql(safeSql);
            List<Map<String, Object>> rows = executeSql(safeSql);
            String answer = formatAnswer(question, rows);
            return new TextToSqlResult(answer, sql, rows);
        }
    }

    @SuppressWarnings("unchecked")
    private String generateSql(String question) {
        String normalizedQuestion = normalizeQuestion(question);
        String prompt = String.format(SCHEMA_PROMPT, MAX_ROWS, normalizedQuestion);

        try {
            log.debug("Text-to-SQL prompt:\n{}", prompt);
            Map<String, Object> request = Map.of(
                    "model", sqlModelName,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "stream", false,
                    "options", Map.of(
                            "temperature", 0,
                            "num_gpu", 0,
                            "num_predict", 512,
                            "repeat_penalty", 1.2,
                            "repeat_last_n", 128
                    )
            );

            Map<String, Object> response = restClient.post()
                    .uri(ollamaBaseUrl + "/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            Map<String, Object> message = (Map<String, Object>) response.get("message");
            String raw = ((String) message.get("content")).trim();
            raw = extractSql(raw);
            log.info("Text-to-SQL generated: {}", raw.replace("\n", " "));
            return raw;
        } catch (TextToSqlException e) {
            throw e;
        } catch (Exception e) {
            throw new TextToSqlException("Failed to generate SQL: " + e.getMessage(), e);
        }
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

    private void validateSql(String sql) {
        if (!SELECT_ONLY.matcher(sql).find()) {
            log.warn("Text-to-SQL validation failed: not a SELECT query. SQL: {}", sql);
            throw new TextToSqlException("Generated SQL is not a SELECT query");
        }
        if (DANGEROUS_SQL.matcher(sql).find()) {
            log.warn("Text-to-SQL validation failed: prohibited keywords. SQL: {}", sql);
            throw new TextToSqlException("Generated SQL contains prohibited keywords");
        }
        if (!sql.toLowerCase().contains("user_id")) {
            log.warn("Text-to-SQL validation failed: missing user_id filter. SQL: {}", sql);
            throw new TextToSqlException("Generated SQL missing user_id filter");
        }
        // Reject multiple statements
        long semiCount = sql.chars().filter(c -> c == ';').count();
        if (semiCount > 0) {
            throw new TextToSqlException("Generated SQL contains multiple statements");
        }
    }

    @SuppressWarnings("deprecation")
    private List<Map<String, Object>> executeSql(String sql) {
        try {
            jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            // Surface the root PostgreSQL error, not just Spring's wrapper
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            String detail = root.getMessage() != null ? root.getMessage() : e.getMessage();
            log.error("Text-to-SQL execution failed. Detail: {}. SQL: {}", detail, sql);
            throw new TextToSqlException("SQL execution failed: " + detail, e);
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
            List<Map<String, Object>> rows
    ) {}

    public static class TextToSqlException extends RuntimeException {
        public TextToSqlException(String message) { super(message); }
        public TextToSqlException(String message, Throwable cause) { super(message, cause); }
    }
}
