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
 * Temporary: logs outgoing HTTP request/response bodies to diagnose llama.cpp 400 errors.
 * Remove after debugging.
 */
@Slf4j
@Configuration
public class RestClientLoggingConfig {

    @Bean
    RestClientCustomizer loggingCustomizer() {
        return builder -> builder
                .requestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()))
                .requestInterceptor(new ClientHttpRequestInterceptor() {
                    @Override
                    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                                         ClientHttpRequestExecution execution) throws IOException {
                        if (request.getURI().getPath().contains("/v1/")) {
                            String bodyStr = new String(body, StandardCharsets.UTF_8);
                            log.info(">>> {} {} headers={} body={}",
                                    request.getMethod(), request.getURI(),
                                    request.getHeaders(),
                                    bodyStr.substring(0, Math.min(bodyStr.length(), 3000)));
                        }
                        ClientHttpResponse response = execution.execute(request, body);
                        if (response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError()) {
                            byte[] responseBody = response.getBody().readAllBytes();
                            String respStr = new String(responseBody, StandardCharsets.UTF_8);
                            log.warn("<<< {} {} status={} response={}",
                                    request.getMethod(), request.getURI(),
                                    response.getStatusCode(),
                                    respStr.substring(0, Math.min(respStr.length(), 2000)));
                        }
                        return response;
                    }
                });
    }
}
