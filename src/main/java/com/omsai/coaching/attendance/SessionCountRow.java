package com.omsai.coaching.attendance;

/**
 * Projection rows of attendance status counts for one class session
 * (used by the date-wise report and the dashboard).
 */
public interface SessionCountRow {

    Long getSessionId();

    long getPresentCount();

    long getAbsentCount();

    long getLateCount();

    long getExcusedCount();
}
