package com.joshfouchey.smsarchive.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
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
            You are a PostgreSQL SQL expert. Generate a SELECT query to answer the user's question about their personal text message archive.

            CONTEXT: This is a personal SMS/MMS archive app. The "user" is the app owner (me). "Contacts" are the people I text with.

            DATABASE SCHEMA:

            messages(id BIGINT, user_id UUID, sender_contact_id BIGINT, conversation_id BIGINT, timestamp TIMESTAMP, body TEXT, direction VARCHAR, protocol VARCHAR)
              - direction = 'INBOUND' (received from contact), 'OUTBOUND' (sent by me)
              - sender_contact_id: NULL for OUTBOUND (I am the sender). For INBOUND, this is the contact who sent it.
              - protocol: 'SMS', 'MMS' (has media), or 'RCS'
              - body contains the text content (may be NULL for image-only MMS)

            contacts(id BIGINT, user_id UUID, number VARCHAR, normalized_number VARCHAR, name VARCHAR)
              - name is the display name (e.g. 'John Doe', 'Mom')
              - Use ILIKE for name matching: WHERE c.name ILIKE '%%John%%'

            conversations(id BIGINT, user_id UUID, name VARCHAR, last_message_at TIMESTAMP)
              - Each conversation is a thread between me and one or more contacts

            conversation_contacts(conversation_id BIGINT, contact_id BIGINT)
              - Links conversations to their participant contacts

            message_parts(id BIGINT, message_id BIGINT, ct VARCHAR, name VARCHAR, file_path VARCHAR, size_bytes BIGINT)
              - ct is MIME type (e.g. 'image/jpeg', 'video/mp4', 'text/plain')

            RULES:
            1. ALWAYS filter by user_id = '__USER_ID__' on every table that has user_id.
            2. Return at most %d rows using LIMIT.
            3. Use human-readable aliases: AS contact_name, AS message_count, etc.
            4. Output ONLY raw SQL — no markdown, no code fences, no explanation.
            5. TEMPORAL SORTING: When grouping by day or month name, use TO_CHAR for display but ALWAYS include the numeric EXTRACT value in ORDER BY for chronological order (e.g., Monday before Tuesday, January before February).
            6. SENDER LOGIC: To find messages SENT BY a contact, filter by m.sender_contact_id. To find all messages WITHIN A CONVERSATION with a contact, join through conversation_contacts.
            7. MEDIA LOGIC: Photos/images are WHERE ct LIKE 'image/%%'. Videos are WHERE ct LIKE 'video/%%'.
            8. Use WITH (CTE) clauses for complex multi-step aggregations to improve readability.

            COMMON QUERY PATTERNS:
            - Messages with a contact: JOIN conversation_contacts cc ON cc.conversation_id = m.conversation_id JOIN contacts c ON c.id = cc.contact_id WHERE c.name ILIKE '%%Name%%'
            - Top contacts: GROUP BY contact name, ORDER BY count DESC
            - Day with most activity: SELECT TO_CHAR(timestamp, 'Day') AS day_name, COUNT(*) AS message_count FROM messages WHERE user_id = '__USER_ID__' GROUP BY day_name, EXTRACT(DOW FROM timestamp) ORDER BY EXTRACT(DOW FROM timestamp)
            - Average messages per conversation: SELECT AVG(cnt) FROM (SELECT COUNT(*) AS cnt FROM messages WHERE user_id = '__USER_ID__' GROUP BY conversation_id) AS sub
            - Percentage/ratio breakdown: Return ALL categories with count and percentage. Use the actual column values, not CASE expressions for columns that already exist.
            - First/last message: ORDER BY m.timestamp ASC/DESC LIMIT 1

            Question: %s""";

    private final ChatModel chatModel;
    private final JdbcTemplate jdbcTemplate;

    @Value("${smsarchive.ai.sql.model:qwen2.5-coder:3b}")
    private String sqlModelName;

    public TextToSqlService(ChatModel chatModel, JdbcTemplate jdbcTemplate) {
        this.chatModel = chatModel;
        this.jdbcTemplate = jdbcTemplate;
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
        List<Map<String, Object>> rows = executeSql(safeSql);
        String answer = formatAnswer(question, rows);
        return new TextToSqlResult(answer, sql, rows);
    }

    private String generateSql(String question) {
        String normalizedQuestion = normalizeQuestion(question);
        String prompt = String.format(SCHEMA_PROMPT, MAX_ROWS, normalizedQuestion);

        try {
            ChatResponse response = chatModel.call(
                    new Prompt(prompt, OllamaOptions.builder()
                            .model(sqlModelName)
                            .temperature(0.1)
                            .numCtx(4096)
                            .build()));

            String raw = response.getResult().getOutput().getText().trim();
            // Strip markdown code fences if present
            raw = raw.replaceAll("(?s)^```(?:sql)?\\s*", "").replaceAll("(?s)\\s*```$", "").trim();
            // Remove trailing semicolons
            raw = raw.replaceAll(";\\s*$", "").trim();
            // Fix malformed CTE: "WITH (" → "WITH"
            raw = raw.replaceAll("(?i)^WITH\\s*\\(\\s*\\n?", "WITH ");

            log.info("Text-to-SQL generated: {}", raw.replace("\n", " "));
            return raw;
        } catch (Exception e) {
            throw new TextToSqlException("Failed to generate SQL: " + e.getMessage(), e);
        }
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
            throw new TextToSqlException("SQL execution failed: " + e.getMessage(), e);
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
