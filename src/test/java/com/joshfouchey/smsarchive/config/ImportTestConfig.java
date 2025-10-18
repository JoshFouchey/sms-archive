package com.joshfouchey.smsarchive.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;

@TestConfiguration
public class ImportTestConfig {
    @Bean
    @Qualifier("importTaskExecutor")
    public TaskExecutor importTaskExecutor() {
        // Run import tasks synchronously in tests to avoid daemon thread shutdown issues
        return new SyncTaskExecutor();
    }
}

