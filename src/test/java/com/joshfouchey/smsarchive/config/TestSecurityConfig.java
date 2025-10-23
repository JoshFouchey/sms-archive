package com.joshfouchey.smsarchive.config;

import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.UserRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@TestConfiguration
@Profile("test")
public class TestSecurityConfig {
    // Removed test SecurityFilterChain to let main SecurityConfig apply

    @Bean
    UserDetailsService testUserDetailsService(UserRepository userRepository) {
        return username -> {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPasswordHash())
                    .roles("USER")
                    .build();
        };
    }
}
