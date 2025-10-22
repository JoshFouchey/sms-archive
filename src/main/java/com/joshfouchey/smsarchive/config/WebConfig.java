package com.joshfouchey.smsarchive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${APP_BASE_URL:http://localhost:8080}")
    private String appBaseUrl;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Build list of allowed origins
                List<String> allowedOrigins = new ArrayList<>();

                // Always allow localhost for development
                allowedOrigins.add("http://localhost:*");
                allowedOrigins.add("http://127.0.0.1:*");
                allowedOrigins.add("https://localhost:*");
                allowedOrigins.add("https://127.0.0.1:*");

                // Add production base URL if configured
                if (appBaseUrl != null && !appBaseUrl.isEmpty() && !appBaseUrl.startsWith("http://localhost")) {
                    allowedOrigins.add(appBaseUrl);
                    // Also support www subdomain if applicable
                    if (appBaseUrl.contains("://") && !appBaseUrl.contains("://www.")) {
                        String wwwUrl = appBaseUrl.replace("://", "://www.");
                        allowedOrigins.add(wwwUrl);
                    }
                }

                registry.addMapping("/**")
                        .allowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**")
                .addResourceLocations("file:media/"); // adjust absolute path if needed
    }
}
