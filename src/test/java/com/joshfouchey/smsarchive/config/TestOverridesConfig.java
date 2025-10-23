package com.joshfouchey.smsarchive.config;

import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.UserRepository;
import com.joshfouchey.smsarchive.service.CurrentUserProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@Profile("test")
public class TestOverridesConfig {

    @Bean
    @Primary
    public CurrentUserProvider testCurrentUserProvider(UserRepository userRepository, Environment environment) {
        return new CurrentUserProvider(userRepository, environment) {
            @Override
            public User getCurrentUser() {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth == null || !auth.isAuthenticated()) {
                    return userRepository.findByUsername("test_fallback").orElseGet(() -> {
                        User u = new User();
                        u.setUsername("test_fallback");
                        u.setPasswordHash("x");
                        return userRepository.save(u);
                    });
                }
                return userRepository.findByUsername(auth.getName()).orElseGet(() -> {
                    User u = new User();
                    u.setUsername(auth.getName());
                    u.setPasswordHash("x");
                    return userRepository.save(u);
                });
            }
        };}
}
