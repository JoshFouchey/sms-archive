package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class TokenService {

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public TokenService(
            @Value("${auth.jwt.secret:}") String secret,
            @Value("${auth.jwt.access-ttl-seconds:3600}") long accessTtlSeconds,
            @Value("${auth.jwt.refresh-ttl-seconds:604800}") long refreshTtlSeconds
    ) {
        if (secret == null || secret.isBlank()) {
            byte[] bytes = new byte[48];
            new SecureRandom().nextBytes(bytes);
            secret = Base64.getEncoder().encodeToString(bytes);
        }
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public Map<String,String> generateTokens(User user) {
        String access = generateToken(user.getUsername(), "access", accessTtlSeconds);
        String refresh = generateToken(user.getUsername(), "refresh", refreshTtlSeconds);
        return Map.of("accessToken", access, "refreshToken", refresh);
    }

    private String generateToken(String username, String type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(username)
                .claim("type", type)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(ttlSeconds)))
                .setId(UUID.randomUUID().toString())
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Optional<Jws<Claims>> parse(String token) {
        try {
            return Optional.of(Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean isAccessToken(String token) {
        return parse(token).map(j -> "access".equals(j.getBody().get("type", String.class))).orElse(false);
    }
    public boolean isRefreshToken(String token) {
        return parse(token).map(j -> "refresh".equals(j.getBody().get("type", String.class))).orElse(false);
    }
}