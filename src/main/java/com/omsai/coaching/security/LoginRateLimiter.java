package com.omsai.coaching.security;

import com.omsai.coaching.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory throttling for the login form. Failed attempts are
 * counted per username + client IP within a sliding window; once the
 * limit is reached, further attempts are rejected until the window
 * expires. Successful sign-in clears the counter.
 *
 * <p>This protects a single application node. For a multi-node
 * deployment a shared store (for example Redis) would be required.</p>
 */
@Component
public class LoginRateLimiter {

    private static final class Window {
        private int failures;
        private Instant firstFailureAt;

        private Window(Instant firstFailureAt) {
            this.firstFailureAt = firstFailureAt;
        }
    }

    private final int maxAttempts;
    private final int windowMinutes;
    private final Map<String, Window> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(AppProperties properties) {
        this.maxAttempts = properties.getLoginRateLimit().getMaxAttempts();
        this.windowMinutes = properties.getLoginRateLimit().getWindowMinutes();
    }

    /** Registers a failed attempt for the given username and IP. */
    public void recordFailure(String username, String ip) {
        String key = key(username, ip);
        Instant now = Instant.now();
        attempts.compute(key, (k, window) -> {
            if (window == null || window.firstFailureAt.plusSeconds(windowMinutes * 60L).isBefore(now)) {
                Window fresh = new Window(now);
                fresh.failures = 1;
                return fresh;
            }
            window.failures++;
            return window;
        });
        cleanup(now);
    }

    /** True when too many failures have been recorded for this key. */
    public boolean isBlocked(String username, String ip) {
        Window window = attempts.get(key(username, ip));
        if (window == null) {
            return false;
        }
        Instant now = Instant.now();
        if (window.firstFailureAt.plusSeconds(windowMinutes * 60L).isBefore(now)) {
            attempts.remove(key(username, ip));
            return false;
        }
        return window.failures >= maxAttempts;
    }

    /** Clears the counter after a successful sign-in. */
    public void clear(String username, String ip) {
        attempts.remove(key(username, ip));
    }

    private String key(String username, String ip) {
        return (username == null ? "" : username.trim().toLowerCase()) + "|" + (ip == null ? "" : ip);
    }

    /** Removes expired entries so the map cannot grow without bound. */
    private void cleanup(Instant now) {
        if (attempts.size() < 1000) {
            return;
        }
        Iterator<Map.Entry<String, Window>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            Window window = it.next().getValue();
            if (window.firstFailureAt.plusSeconds(windowMinutes * 60L).isBefore(now)) {
                it.remove();
            }
        }
    }
}
