package com.joshfouchey.smsarchive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // enable @Async for long-running import tasks
public class SmsArchiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmsArchiveApplication.class, args);
    }

}
