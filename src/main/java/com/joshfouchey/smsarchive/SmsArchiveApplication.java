package com.joshfouchey.smsarchive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync // enable @Async for long-running import tasks
@EnableScheduling // enable @Scheduled for periodic tasks (import directory watcher)
public class SmsArchiveApplication {

    static {
        // Apply XML parser limits early (before any XML parsing) with environment overrides.
        // These mitigate large entity / text node truncation for very large backup files.
        setIfAbsent("jdk.xml.maxGeneralEntitySizeLimit", envOrDefault("XML_MAX_GENERAL_ENTITY_SIZE", "5000000"));
        setIfAbsent("jdk.xml.totalEntitySizeLimit", envOrDefault("XML_TOTAL_ENTITY_SIZE_LIMIT", "0")); // 0 = unlimited
        setIfAbsent("jdk.xml.entityExpansionLimit", envOrDefault("XML_ENTITY_EXPANSION_LIMIT", "0")); // 0 = unlimited
    }

    private static String envOrDefault(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    private static void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(SmsArchiveApplication.class, args);
    }

}
