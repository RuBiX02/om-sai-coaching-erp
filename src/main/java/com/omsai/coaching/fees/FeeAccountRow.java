package com.omsai.coaching.fees;

/**
 * Projection row of fee-account listings with computed Outstanding
 * Balance (net fee minus RECEIVED payments). Aliases use plain
 * camelCase so PostgreSQL's lowercase folding still matches.
 */
public interface FeeAccountRow {

    Long getId();

    String getStudentName();

    String getStudentCode();

    String getCourseName();

    String getAcademicYear();

    java.math.BigDecimal getGrossFee();

    java.math.BigDecimal getDiscountAmount();

    java.time.LocalDate getDueDate();

    java.math.BigDecimal getOutstanding();
}
