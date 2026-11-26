package com.joshfouchey.smsarchive.config;

import com.joshfouchey.smsarchive.util.RateLimiter;
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
        drainOnShutdown(exec);
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
        drainOnShutdown(exec);
        exec.initialize();
        return exec;
    }

    /**
     * Let running and queued tasks finish for up to 25s on shutdown instead of interrupting them.
     * Runs after Boot's 30s graceful web phase, so the container stop grace period must cover both (compose: 60s).
     */
    static void drainOnShutdown(ThreadPoolTaskExecutor exec) {
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(25);
    }

    /** Rate limiter for AI endpoints: 10 requests per minute per user. */
    @Bean
    public RateLimiter aiRateLimiter() {
        return new RateLimiter(10, 60_000);
    }
}

