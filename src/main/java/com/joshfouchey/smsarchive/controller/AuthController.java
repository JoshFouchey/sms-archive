package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.UserRepository;
import com.joshfouchey.smsarchive.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    record AuthRequest(String username, String password) {}
    record RefreshRequest(String refreshToken) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest req) {
        if (req.username() == null || req.username().isBlank() || req.password() == null || req.password().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error","Invalid username or password"));
        }
        if (userRepository.existsByUsername(req.username())) {
            return ResponseEntity.badRequest().body(Map.of("error","Username already taken"));
        }
        User u = new User();
        u.setUsername(req.username());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        userRepository.save(u);
        return ResponseEntity.ok(tokenService.generateTokens(u));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        return userRepository.findByUsername(req.username())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(tokenService.generateTokens(u)))
                .orElseGet(() -> ResponseEntity.status(401).body(Map.of("error","Invalid credentials")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest req) {
        if (req.refreshToken() == null || req.refreshToken().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error","Missing refresh token"));
        }
        return tokenService.parse(req.refreshToken())
                .filter(j -> "refresh".equals(j.getBody().get("type", String.class)))
                .flatMap(j -> userRepository.findByUsername(j.getBody().getSubject()))
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(tokenService.generateTokens(user)))
                .orElseGet(() -> ResponseEntity.status(400).body(Map.of("error","Invalid refresh token")));
    }
}
