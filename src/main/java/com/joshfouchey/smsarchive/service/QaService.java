package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.*;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.service.TextToSqlService.TextToSqlException;
import com.joshfouchey.smsarchive.service.TextToSqlService.TextToSqlResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

import static com.joshfouchey.smsarchive.service.UnifiedSearchService.SearchMode;

@Service
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class QaService {

    private static final Logger log = LoggerFactory.getLogger(QaService.class);

 // Pattern to detect data/analytics questions that text-to-SQL can handle
    // Uses word boundaries to avoid matching inside unrelated words
    private static final Pattern DATA_QUESTION_PATTERN = Pattern.compile(
            "(how\\s+many|how\\s+often|" +
            "\\bcount\\b|\\btotal\\b|\\baverage\\b|" +
            "first\\s+(text|message)|last\\s+(text|message)|" +
            "most\\s+(recent|common|frequent|active|text|message|contact)|" +
            "least\\s+(recent|active)|" +
            "busiest|longest|earliest|latest|" +
            "per\\s+(day|week|month|year)|" +
            "\\bsince\\s+\\d{4}|\\bin\\s+\\d{4}\\b|" +
            "statistics|frequency|percentage|ratio|" +
            "sent\\s+to|received\\s+from|" +
            "which\\s+(month|year|day|week)|" +
            "when\\s+did\\s+i\\s+(first|last)|" +
            "who\\s+do\\s+i\\s+(text|message|talk|chat)\\s+(the\\s+)?most|" +
            "top\\s+\\d*\\s*contact|" +
            "compared|breakdown)",
            Pattern.CASE_INSENSITIVE
    );

    private final UnifiedSearchService unifiedSearchService;

    @Autowired(required = false)
    private TextToSqlService textToSqlService;

    public QaService(UnifiedSearchService unifiedSearchService) {
        this.unifiedSearchService = unifiedSearchService;
    }

    public QaResponse ask(User user, QaRequest request) {
        long start = System.currentTimeMillis();
        String question = request.question().trim();
        String mode = request.mode() != null ? request.mode().toUpperCase() : "AUTO";

        return switch (mode) {
            case "DATA" -> askData(user, question, request, start);
            case "AI", "SEARCH" -> handleSearch(user, question, request, start);
            default -> askAuto(user, question, request, start);
        };
    }

    public QaResponse runSql(User user, String sql) {
        long start = System.currentTimeMillis();
        if (textToSqlService == null) {
            return QaResponse.analytics("Data query service is not available. Check that the SQL model is configured.", null,
                    System.currentTimeMillis() - start);
        }

        try {
            TextToSqlResult result = textToSqlService.executeUserSql(sql, user.getId());
            return analyticsResponse(result, System.currentTimeMillis() - start);
        } catch (TextToSqlException e) {
            log.warn("Manual SQL execution failed for user {}: {}", user.getId(), e.getMessage());
            return QaResponse.analytics("SQL execution failed. Check your query and try again.", sqlErrorData(e),
                    System.currentTimeMillis() - start);
        }
    }

    /** DATA mode: text-to-SQL only */
    private QaResponse askData(User user, String question, QaRequest request, long start) {
        if (textToSqlService != null) {
            try {
                return handleTextToSql(user, question, start);
            } catch (TextToSqlException e) {
                log.warn("Text-to-SQL failed for user {} on '{}': {}", user.getId(), question, e.getMessage());
                String errorMsg = "SQL generation failed. Try rephrasing your question.";
                return QaResponse.analytics(errorMsg, sqlErrorData(e), System.currentTimeMillis() - start);
            }
        }

        return QaResponse.analytics("Data query service is not available. Check that the SQL model is configured.", null,
                System.currentTimeMillis() - start);
    }

    /** AUTO mode: text-to-SQL → search */
    private QaResponse askAuto(User user, String question, QaRequest request, long start) {
        // 1. Text-to-SQL for data questions
        if (textToSqlService != null && DATA_QUESTION_PATTERN.matcher(question).find()) {
            try {
                return handleTextToSql(user, question, start);
            } catch (TextToSqlException e) {
                log.info("Text-to-SQL failed in auto mode for user {}: {}", user.getId(), e.getMessage());
            }
        }

        // 2. Search fallback
        return handleSearch(user, question, request, start);
    }

    // --- Pipeline Handlers ---

    private QaResponse handleTextToSql(User user, String question, long startTime) {
        TextToSqlResult result = textToSqlService.generateAndExecute(question, user.getId());
        return analyticsResponse(result, System.currentTimeMillis() - startTime);
    }

    private QaResponse analyticsResponse(TextToSqlResult result, long elapsed) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "sql_result");
        data.put("generatedSql", result.generatedSql());
        data.put("executedSql", result.executedSql());
        data.put("generationMs", result.generationMs());
        data.put("executionMs", result.executionMs());
        if (!result.rows().isEmpty()) {
            data.put("columns", new ArrayList<>(result.rows().get(0).keySet()));
        } else {
            data.put("columns", List.of());
        }
        data.put("rows", result.rows());
        data.put("rowCount", result.rows().size());

        return QaResponse.analytics(result.answer(), data, elapsed);
    }

    private Map<String, Object> sqlErrorData(TextToSqlException e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "sql_error");
        data.put("error", e.getMessage());
        data.put("dbError", e.getDbError());
        data.put("generatedSql", e.getGeneratedSql());
        data.put("executedSql", e.getExecutedSql());
        return data;
    }

    private QaResponse handleSearch(User user, String question, QaRequest request, long startTime) {
        var results = unifiedSearchService.search(
                question, SearchMode.AUTO, user.getId(),
                request.conversationId(), request.contactId(), 20);
        long elapsed = System.currentTimeMillis() - startTime;
        return QaResponse.search(results, elapsed);
    }
}
