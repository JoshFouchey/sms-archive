package com.joshfouchey.smsarchive.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CacheConfig.class)
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class));

    @Test
    void usesCaffeineWhenCacheTypeUnset() {
        runner.run(ctx -> assertThat(ctx.getBean(CacheManager.class)).isInstanceOf(CaffeineCacheManager.class));
    }

    @Test
    void usesCaffeineWhenCacheTypeIsCaffeine() {
        runner.withPropertyValues("spring.cache.type=caffeine")
                .run(ctx -> assertThat(ctx.getBean(CacheManager.class)).isInstanceOf(CaffeineCacheManager.class));
    }

    @Test
    void backsOffWhenCacheTypeIsNone() {
        runner.withPropertyValues("spring.cache.type=none")
                .run(ctx -> assertThat(ctx.getBean(CacheManager.class)).isInstanceOf(NoOpCacheManager.class));
    }
}
