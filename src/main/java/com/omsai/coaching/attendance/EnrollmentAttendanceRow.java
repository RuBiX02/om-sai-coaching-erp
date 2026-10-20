package com.omsai.coaching.attendance;

/**
 * Projection rows of attendance status counts per enrollment.
 */
public interface EnrollmentAttendanceRow {

    Long getEnrollmentId();

    long getPresentCount();

    long getAbsentCount();

    long getLateCount();

    long getExcusedCount();
}
