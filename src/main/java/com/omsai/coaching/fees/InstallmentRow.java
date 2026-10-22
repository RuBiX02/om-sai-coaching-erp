package com.omsai.coaching.fees;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projection of one installment in the management reports: the
 * schedule, the effective paid amount (allocations of RECEIVED
 * payments only) and the student context. Aliases use plain camelCase
 * so PostgreSQL's lowercase folding still matches these properties
 * case-insensitively.
 */
public interface InstallmentRow {

    Long getId();

    Long getFeeAccountId();

    Integer getInstallmentNumber();

    LocalDate getDueDate();

    BigDecimal getAmount();

    BigDecimal getPaid();

    /** Days between the due date and the business date (negative while not yet due). */
    Integer getDaysOverdue();

    String getStudentName();

    String getStudentCode();

    String getCourseName();

    String getAcademicYear();
}
