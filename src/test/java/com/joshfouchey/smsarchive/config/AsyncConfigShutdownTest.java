package com.joshfouchey.smsarchive.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigShutdownTest {

    @ParameterizedTest
    @ValueSource(strings = {"import", "ai"})
    void shutdownLetsRunningTaskFinishInsteadOfInterruptingIt(String which) throws Exception {
        AsyncConfig config = new AsyncConfig();
        TaskExecutor executor = which.equals("import") ? config.importTaskExecutor() : config.aiTaskExecutor();
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();

        executor.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(300);
                completed.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        ((ThreadPoolTaskExecutor) executor).shutdown();

        assertThat(completed).isTrue();
    }
}
