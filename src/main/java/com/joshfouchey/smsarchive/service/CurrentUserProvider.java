package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class CurrentUserProvider {
    private final UserRepository userRepository;
    private final Environment environment;

    public CurrentUserProvider(UserRepository userRepository, Environment environment) {
        this.userRepository = userRepository;
        this.environment = environment;
    }

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean testProfile = Arrays.asList(environment.getActiveProfiles()).contains("test");
        if (auth == null || !auth.isAuthenticated()) {
            if (testProfile) {
                return userRepository.findByUsername("test_fallback").orElseGet(() -> {
                    User u = new User();
                    u.setUsername("test_fallback");
                    u.setPasswordHash("x");
                    return userRepository.save(u);
                });
            }
            throw new IllegalStateException("No authenticated user in context");
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username));
    }
}
