package com.joshfouchey.smsarchive.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest extends EnhancedPostgresTestContainer {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ContactRepository contactRepository;
    @Autowired MessageRepository messageRepository;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void cleanup() {
        messageRepository.deleteAll();
        contactRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── Helpers ─────────────────────────────────────────────────

    private String json(Object obj) throws Exception {
        return mapper.writeValueAsString(obj);
    }

    /** Register a user and return the full token response JSON node. */
    private JsonNode registerUser(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    /** Login and return the full token response JSON node. */
    private JsonNode loginUser(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    // ── Registration ────────────────────────────────────────────

    @Nested
    class Registration {

        @Test
        void successReturnsTokens() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "newuser", "password", "password123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isString())
                    .andExpect(jsonPath("$.refreshToken").isString());
        }

        @Test
        void duplicateUsernameRejected() throws Exception {
            registerUser("dupuser", "password123");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "dupuser", "password", "password123"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Username already taken"));
        }

        @Test
        void blankUsernameRejected() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "  ", "password", "password123"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shortPasswordRejected() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "shortpw", "password", "12345"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void caseInsensitiveDuplicateRejected() throws Exception {
            registerUser("caseuser", "password123");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "CaseUser", "password", "password123"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Username already taken"));
        }

        @Test
        void usernameStoredLowercase() throws Exception {
            registerUser("MixedCase", "password123");
            assert userRepository.findByUsername("mixedcase").isPresent();
        }
    }

    // ── Login ───────────────────────────────────────────────────

    @Nested
    class Login {

        @Test
        void validCredentialsReturnTokens() throws Exception {
            registerUser("loginuser", "password123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "loginuser", "password", "password123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isString())
                    .andExpect(jsonPath("$.refreshToken").isString());
        }

        @Test
        void wrongPasswordReturns401() throws Exception {
            registerUser("wrongpw", "password123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "wrongpw", "password", "wrongpass"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid credentials"));
        }

        @Test
        void nonexistentUserReturns401() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "ghost", "password", "password123"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void caseInsensitiveLogin() throws Exception {
            registerUser("cilogin", "password123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "CILOGIN", "password", "password123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isString());
        }
    }

    // ── Refresh Token ───────────────────────────────────────────

    @Nested
    class RefreshToken {

        @Test
        void validRefreshTokenReturnsNewTokens() throws Exception {
            JsonNode tokens = registerUser("refreshuser", "password123");
            String refreshToken = tokens.get("refreshToken").asText();

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("refreshToken", refreshToken))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isString())
                    .andExpect(jsonPath("$.refreshToken").isString());
        }

        @Test
        void accessTokenRejectedAsRefresh() throws Exception {
            JsonNode tokens = registerUser("atrefresh", "password123");
            String accessToken = tokens.get("accessToken").asText();

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("refreshToken", accessToken))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid refresh token"));
        }

        @Test
        void garbageTokenRejected() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("refreshToken", "not.a.real.token"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void blankTokenRejected() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("refreshToken", ""))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── Security Config Access Rules ────────────────────────────

    @Nested
    class SecurityRules {

        @Test
        void authEndpointsAccessibleWithoutToken() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "x", "password", "x"))))
                    .andExpect(status().isUnauthorized()); // 401 from bad creds, NOT from security filter

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "x", "password", "short"))))
                    .andExpect(status().isBadRequest()); // 400 from validation, NOT 401 from filter
        }

        @Test
        void protectedEndpointRejectsUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/messages/contacts"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void protectedEndpointAcceptsValidAccessToken() throws Exception {
            JsonNode tokens = registerUser("secuser", "password123");
            String accessToken = tokens.get("accessToken").asText();

            mockMvc.perform(get("/api/messages/contacts")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());
        }

        @Test
        void refreshTokenCannotAccessProtectedEndpoints() throws Exception {
            JsonNode tokens = registerUser("reftok", "password123");
            String refreshToken = tokens.get("refreshToken").asText();

            mockMvc.perform(get("/api/messages/contacts")
                            .header("Authorization", "Bearer " + refreshToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void contactsEndpointRequiresAuth() throws Exception {
            mockMvc.perform(get("/api/contacts"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void meEndpointRequiresAuth() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void meEndpointReturnsUserWithValidToken() throws Exception {
            JsonNode tokens = registerUser("meuser", "password123");
            String accessToken = tokens.get("accessToken").asText();

            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("meuser"))
                    .andExpect(jsonPath("$.id").isString());
        }

        @Test
        void healthEndpointAccessibleWithoutToken() throws Exception {
            // actuator/health is permitAll for Docker health checks
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }
    }
}
