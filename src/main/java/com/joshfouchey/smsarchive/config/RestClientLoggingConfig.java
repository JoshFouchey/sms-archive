package com.joshfouchey.smsarchive.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Temporary: logs outgoing HTTP request bodies to diagnose llama.cpp 400 errors.
 * Remove after debugging.
 */
@Slf4j
@Configuration
public class RestClientLoggingConfig {

    @Bean
    RestClientCustomizer loggingCustomizer() {
        return builder -> builder.requestInterceptor(new ClientHttpRequestInterceptor() {
            @Override
            public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                                 ClientHttpRequestExecution execution) throws IOException {
                if (request.getURI().getPath().contains("/v1/")) {
                    log.info(">>> {} {} body={}", request.getMethod(), request.getURI(),
                            new String(body, StandardCharsets.UTF_8).substring(0, Math.min(body.length, 2000)));
                }
                ClientHttpResponse response = execution.execute(request, body);
                if (response.getStatusCode().is4xxClientError()) {
                    log.warn("<<< {} {} status={}", request.getMethod(), request.getURI(),
                            response.getStatusCode());
                }
                return response;
            }
        });
    }
}
