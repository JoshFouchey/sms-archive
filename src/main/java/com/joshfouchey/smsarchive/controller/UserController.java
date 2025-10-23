package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.service.CurrentUserProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Simple controller to expose the currently authenticated user's basic profile.
 * This intentionally keeps the response minimal (no password hash, etc.).
 */
@RestController
public class UserController {

    private final CurrentUserProvider currentUserProvider;

    public UserController(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    public record CurrentUserResponse(UUID id, String username, Instant createdAt, Instant updatedAt) {}

    @GetMapping("/api/auth/me")
    public ResponseEntity<CurrentUserResponse> me() {
        try {
            User u = currentUserProvider.getCurrentUser();
            return ResponseEntity.ok(new CurrentUserResponse(u.getId(), u.getUsername(), u.getCreatedAt(), u.getUpdatedAt()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).build();
        }
    }
}

