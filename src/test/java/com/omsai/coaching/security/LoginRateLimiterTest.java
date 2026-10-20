package com.omsai.coaching.security;

import com.omsai.coaching.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the in-memory login throttling rules: counted
 * failures, per-username + IP keys, clearing after success.
 */
class LoginRateLimiterTest {

    private LoginRateLimiter limiterWithMax(int maxAttempts) {
        AppProperties properties = new AppProperties();
        properties.getLoginRateLimit().setMaxAttempts(maxAttempts);
        properties.getLoginRateLimit().setWindowMinutes(15);
        return new LoginRateLimiter(properties);
    }

    @Test
    void firstFailureIsCountedImmediately() {
        LoginRateLimiter limiter = limiterWithMax(3);
        limiter.recordFailure("user@omsai.local", "1.2.3.4");
        limiter.recordFailure("user@omsai.local", "1.2.3.4");

        boolean blocked = limiter.isBlocked("user@omsai.local", "1.2.3.4");
        assertThat(blocked).as("two out of three failures must not block yet").isFalse();
    }

    @Test
    void blockAfterMaxFailures() {
        LoginRateLimiter limiter = limiterWithMax(3);
        limiter.recordFailure("user@omsai.local", "1.2.3.4");
        limiter.recordFailure("user@omsai.local", "1.2.3.4");
        limiter.recordFailure("user@omsai.local", "1.2.3.4");

        assertThat(limiter.isBlocked("user@omsai.local", "1.2.3.4")).isTrue();
    }

    @Test
    void keysAreCaseInsensitiveOnUsernameAndSeparatePerIp() {
        LoginRateLimiter limiter = limiterWithMax(2);
        limiter.recordFailure("SomeUser@omsai.local", "1.1.1.1");
        limiter.recordFailure("someuser@OMSAI.local", "1.1.1.1");

        // same key (username lower-cased), enough failures to block
        assertThat(limiter.isBlocked("someuser@OMSAI.local", "1.1.1.1")).isTrue();
        // different IP keeps a separate counter
        assertThat(limiter.isBlocked("someuser@omsai.local", "2.2.2.2")).isFalse();
    }

    @Test
    void clearRemovesTheCounter() {
        LoginRateLimiter limiter = limiterWithMax(1);
        limiter.recordFailure("user@omsai.local", "1.2.3.4");
        assertThat(limiter.isBlocked("user@omsai.local", "1.2.3.4")).isTrue();

        limiter.clear("user@omsai.local", "1.2.3.4");
        assertThat(limiter.isBlocked("user@omsai.local", "1.2.3.4")).isFalse();
    }

    @Test
    void nullInputsDoNotFail() {
        LoginRateLimiter limiter = limiterWithMax(5);
        limiter.recordFailure(null, null);
        limiter.recordFailure("", "");
        assertThat(limiter.isBlocked(null, null)).isFalse();
        assertThat(limiter.isBlocked("", null)).isFalse();
    }
}
