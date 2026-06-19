package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.QaRequest;
import com.joshfouchey.smsarchive.dto.QaResponse;
import com.joshfouchey.smsarchive.dto.SqlRunRequest;
import com.joshfouchey.smsarchive.service.CurrentUserProvider;
import com.joshfouchey.smsarchive.service.QaService;
import com.joshfouchey.smsarchive.util.RateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.joshfouchey.smsarchive.util.InputLimits.*;

@RestController
@RequestMapping("/api/qa")
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class QaController {

    private final QaService qaService;
    private final CurrentUserProvider currentUserProvider;
    private final RateLimiter aiRateLimiter;

    public QaController(QaService qaService, CurrentUserProvider currentUserProvider, RateLimiter aiRateLimiter) {
        this.qaService = qaService;
        this.currentUserProvider = currentUserProvider;
        this.aiRateLimiter = aiRateLimiter;
    }

    private ResponseEntity<QaResponse> rateLimitCheck(UUID userId) {
        if (!aiRateLimiter.tryAcquire(userId.toString())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(QaResponse.analytics("Rate limited. Try again in a moment.", null, 0));
        }
        return null;
    }

      @PostMapping("/ask")
    public ResponseEntity<QaResponse> ask(@RequestBody QaRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Question is required"));
        }
        if (request.question().length() > QA_QUESTION_MAX) {
            return ResponseEntity.badRequest().body(Map.of("error", "Question exceeds maximum length of " + QA_QUESTION_MAX + " characters"));
        }
        var user = currentUserProvider.getCurrentUser();
        var limited = rateLimitCheck(user.getId());
        if (limited != null) return limited;
        QaRequest safe = new QaRequest(
                request.question(),
                request.mode(), request.conversationId(), request.contactId());
        var response = qaService.ask(user, safe);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sql/run")
    public ResponseEntity<QaResponse> runSql(@RequestBody SqlRunRequest request) {
        if (request.sql() == null || request.sql().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "SQL query is required"));
        }
        if (request.sql().length() > SQL_QUERY_MAX) {
            return ResponseEntity.badRequest().body(Map.of("error", "SQL query exceeds maximum length of " + SQL_QUERY_MAX + " characters"));
        }
        var user = currentUserProvider.getCurrentUser();
        var limited = rateLimitCheck(user.getId());
        if (limited != null) return limited;
        return ResponseEntity.ok(qaService.runSql(user, request.sql()));
    }
}
