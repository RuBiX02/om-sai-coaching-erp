package com.omsai.coaching.attendance;

/**
 * Projection rows of aggregate "eligible completed sessions" counts
 * per enrollment (raw percentages are computed in the service).
 * Aliases use plain camelCase so that PostgreSQL's lowercase folding
 * still matches these properties case-insensitively.
 */
public interface EnrollmentSessionRow {

    Long getEnrollmentId();

    String getStudentCode();

    String getStudentName();

    java.time.LocalDate getEnrolledFrom();

    java.time.LocalDate getEnrolledTo();

    long getSessionCount();
}
