package com.joshfouchey.smsarchive.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

/**
 * Enhanced singleton PostgreSQL Testcontainer for integration tests (jsonb, GIN, triggers).
 * Reuse: add testcontainers.reuse.enable=true to ~/.testcontainers.properties (not committed).
 * Includes Hikari tuning & startup robustness.
 */
public abstract class EnhancedPostgresTestContainer {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("sms_archive_test")
            .withUsername("postgres")
            .withPassword("postgres")
            .withReuse(true)
            .withStartupAttempts(3)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)));

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        // Hikari tuning for tests
        registry.add("spring.datasource.hikari.maximumPoolSize", () -> 5);
        registry.add("spring.datasource.hikari.minimumIdle", () -> 1);
        registry.add("spring.datasource.hikari.maxLifetime", () -> 60000);
        registry.add("spring.datasource.hikari.idleTimeout", () -> 30000);
        registry.add("spring.datasource.hikari.validationTimeout", () -> 5000);
        // Hibernate optimization
        registry.add("spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation", () -> true);
    }
}
