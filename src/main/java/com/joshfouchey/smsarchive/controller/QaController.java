package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.QaRequest;
import com.joshfouchey.smsarchive.dto.QaResponse;
import com.joshfouchey.smsarchive.service.CurrentUserProvider;
import com.joshfouchey.smsarchive.service.QaService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/qa")
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class QaController {

    private final QaService qaService;
    private final CurrentUserProvider currentUserProvider;

    public QaController(QaService qaService, CurrentUserProvider currentUserProvider) {
        this.qaService = qaService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/ask")
    public ResponseEntity<QaResponse> ask(@RequestBody QaRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        var user = currentUserProvider.getCurrentUser();
        var response = qaService.ask(user, request);
        return ResponseEntity.ok(response);
    }
}
