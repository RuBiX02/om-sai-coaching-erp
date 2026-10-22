package com.omsai.coaching.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * The application's business clock. All "today", "this month" and
 * closure-date decisions use the configured business timezone
 * (app.business-timezone) so that reports and stored dates are
 * consistent with the institute's calendar, not with the server's
 * default zone.
 */
@Component
public class BusinessClock {

    private final ZoneId zoneId;

    public BusinessClock(@Value("${app.business-timezone:Asia/Kolkata}") String timezone) {
        this.zoneId = ZoneId.of(timezone);
    }

    public ZoneId zone() {
        return zoneId;
    }

    /** Today's date in the business timezone. */
    public LocalDate today() {
        return LocalDate.now(zoneId);
    }

    /** The current month in the business timezone. */
    public YearMonth currentMonth() {
        return YearMonth.now(zoneId);
    }

    /** The current instant (for audit timestamps). */
    public Instant now() {
        return Instant.now();
    }

    /**
     * The current date and time in the business timezone. Used for
     * day-dependent presentation (for example the greeting on the
     * dashboard), so the screen always agrees with the institute's
     * calendar rather than with the server's default zone.
     */
    public LocalDateTime nowLocal() {
        return LocalDateTime.now(zoneId);
    }
}
