package com.omsai.coaching;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the application context boots against the PostgreSQL
 * test database ("omsai_test") with Flyway migrations applied.
 */
@SpringBootTest
@ActiveProfiles("test")
class CoachingErpApplicationTests {

    @Test
    void contextLoads() {
    }
}
