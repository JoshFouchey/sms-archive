package com.joshfouchey.smsarchive.controller;

import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics/cache")
public class AnalyticsAdminController {

    private final CacheManager cacheManager;

    public AnalyticsAdminController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @PostMapping("/evict")
    public ResponseEntity<String> evictDashboardCache() {
        if (cacheManager.getCache("analyticsDashboard") != null) {
            cacheManager.getCache("analyticsDashboard").clear();
        }
        return ResponseEntity.ok("analyticsDashboard cache cleared");
    }
}

