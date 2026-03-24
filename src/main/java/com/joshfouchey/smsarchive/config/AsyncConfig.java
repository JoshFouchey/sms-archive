package com.joshfouchey.smsarchive.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "importTaskExecutor")
    public TaskExecutor importTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(10_000); // large queue for many messages batches
        exec.setThreadNamePrefix("import-worker-");
        exec.initialize();
        return exec;
    }

    @Bean(name = "aiTaskExecutor")
    public TaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);      // Only 1 AI task at a time (VRAM constraint)
        exec.setMaxPoolSize(1);
        exec.setQueueCapacity(5);
        exec.setThreadNamePrefix("ai-worker-");
        exec.initialize();
        return exec;
    }
}

