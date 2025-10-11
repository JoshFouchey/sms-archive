package com.joshfouchey.smsarchive.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public Caffeine<Object, Object> caffeineSpec() {
        return Caffeine.newBuilder()
                .maximumSize(100)              // plenty for parameter combos
                .expireAfterWrite(Duration.ofHours(6)) // long lived; explicit eviction on import
                .recordStats();
    }

    @Bean
    public CacheManager cacheManager(Caffeine<Object,Object> caffeine) {
        SimpleCacheManager mgr = new SimpleCacheManager();
        mgr.setCaches(List.of(
                new CaffeineCache("analyticsDashboard", caffeine.build())
        ));
        return mgr;
    }
}

