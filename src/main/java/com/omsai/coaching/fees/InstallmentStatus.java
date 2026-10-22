package com.omsai.coaching.fees;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The derived state of an installment. Nothing is stored: the status
 * is computed from the due date, the effective allocations and the
 * business date, so it can never drift away from the money.
 */
public enum InstallmentStatus {

    /** Nothing due yet and nothing paid. */
    UPCOMING,
    /** Due today, nothing paid yet. */
    DUE_TODAY,
    /** Something was paid, a balance remains and the due date has not passed. */
    PARTIALLY_PAID,
    /** The due date has passed and a balance remains (may also be partly paid). */
    OVERDUE,
    /** Nothing remains to pay. */
    PAID;

    /**
     * The status of one installment, with the documented precedence:
     * paid first, then overdue, then partially paid, then due today,
     * then upcoming.
     *
     * @param dueDate the installment's due date
     * @param amount  the scheduled amount
     * @param paid    the amount effectively paid (allocations of RECEIVED payments)
     * @param today   the business date
     */
    public static InstallmentStatus of(LocalDate dueDate, BigDecimal amount, BigDecimal paid, LocalDate today) {
        BigDecimal remaining = amount.subtract(paid);
        if (remaining.signum() <= 0) {
            return PAID;
        }
        if (dueDate.isBefore(today)) {
            return OVERDUE;
        }
        if (paid.signum() > 0) {
            return PARTIALLY_PAID;
        }
        if (dueDate.isEqual(today)) {
            return DUE_TODAY;
        }
        return UPCOMING;
    }
}
