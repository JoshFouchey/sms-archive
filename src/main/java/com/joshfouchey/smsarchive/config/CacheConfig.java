package com.joshfouchey.smsarchive.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {
    // Provide an explicit CacheManager; auto-config was not creating one in test profile, causing NoSuchBeanDefinition for CacheManager.
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager(
                "analyticsDashboard",
                "distinctContacts",
                "conversationMessages",      // Cache full conversation message lists
                "conversationMessageCount",   // Cache message counts
                "messageContext",            // Cache message context for search navigation
                "contactSummaries",          // Cache contact summaries list
                "conversationList",          // Cache conversation list
                "conversationTimeline",      // Cache conversation timeline buckets
                "currentUser",               // Cache user lookups by username
                "kgEntities",                // Cache KG entity listings
                "kgEntityFacts",             // Cache entity fact lookups
                "kgGraph",                   // Cache KG graph visualization data
                "kgStats"                    // Cache KG entity/triple counts
        );
        mgr.setCaffeine(Caffeine.newBuilder()
                .maximumSize(800)
                .expireAfterWrite(Duration.ofMinutes(15))
                .recordStats());
        return mgr;
    }
}

