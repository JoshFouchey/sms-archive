package com.joshfouchey.smsarchive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync // enable @Async for long-running import tasks
@EnableScheduling // enable @Scheduled for periodic tasks (import directory watcher)
public class SmsArchiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmsArchiveApplication.class, args);
    }

}
