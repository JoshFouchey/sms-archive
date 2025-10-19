package com.joshfouchey.smsarchive;

import com.joshfouchey.smsarchive.config.EnhancedPostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SmsArchiveApplicationTests extends EnhancedPostgresTestContainer {

    @Test
    void contextLoads() {}
}
