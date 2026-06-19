package com.joshfouchey.smsarchive.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory token bucket rate limiter.
 * Per-key limits with configurable tokens and refill interval.
 */
public class RateLimiter {

    private final int maxTokens;
    private final long refillIntervalMs;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int maxTokens, long refillIntervalMs) {
        this.maxTokens = maxTokens;
        this.refillIntervalMs = refillIntervalMs;
    }

    /**
     * Try to acquire a token for the given key.
     * @return true if allowed, false if rate limited
     */
    public boolean tryAcquire(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());
        synchronized (bucket) {
            long now = System.currentTimeMillis();
            if (now - bucket.lastRefillTime >= refillIntervalMs) {
                bucket.tokens = maxTokens;
                bucket.lastRefillTime = now;
            }
            if (bucket.tokens > 0) {
                bucket.tokens--;
                return true;
            }
            return false;
        }
    }

    /**
     * Get remaining tokens for a key (for debug/headers).
     */
    public int remaining(String key) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) return maxTokens;
        synchronized (bucket) {
            long now = System.currentTimeMillis();
            if (now - bucket.lastRefillTime >= refillIntervalMs) {
                return maxTokens;
            }
            return bucket.tokens;
        }
    }

    private static class Bucket {
        int tokens;
        long lastRefillTime;

        Bucket() {
            this.tokens = 0;
            this.lastRefillTime = System.currentTimeMillis();
        }
    }
}
