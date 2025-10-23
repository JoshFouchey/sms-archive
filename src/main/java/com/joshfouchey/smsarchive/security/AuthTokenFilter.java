package com.joshfouchey.smsarchive.security;

import com.joshfouchey.smsarchive.repository.UserRepository;
import com.joshfouchey.smsarchive.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@ConditionalOnBean(com.joshfouchey.smsarchive.service.TokenService.class)
public class AuthTokenFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    public AuthTokenFilter(TokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            tokenService.parse(token).ifPresent(this::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(Jws<Claims> jws) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) return;
        String username = jws.getBody().getSubject();
        userRepository.findByUsername(username).ifPresent(user -> {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);
        });
    }
}
