package com.joshfouchey.smsarchive.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Forces SimpleClientHttpRequestFactory for all RestClients.
 * Spring Boot 3.x defaults to JdkClientHttpRequestFactory which uses chunked
 * transfer encoding. llama.cpp's HTTP server cannot parse chunked request
 * bodies and returns HTTP 400.
 */
@Configuration
public class RestClientConfig {

    @Bean
    RestClientCustomizer httpRequestFactoryCustomizer() {
        return builder -> builder.requestFactory(new SimpleClientHttpRequestFactory());
    }
}
