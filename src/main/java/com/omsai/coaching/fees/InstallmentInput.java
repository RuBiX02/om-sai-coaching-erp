package com.omsai.coaching.fees;

/**
 * One row of the installment-plan form as submitted by the browser:
 * the raw text of the amount, the due date and the installment number.
 *
 * <p>The values stay raw on purpose - the service parses and validates
 * them and can therefore report exactly which row is wrong. Nothing the
 * browser sends is trusted.</p>
 */
public record InstallmentInput(String amount, String dueDate, String installmentNumber) {

    /** True when the row was left completely empty (it is then ignored). */
    public boolean isEmpty() {
        return isBlank(amount) && isBlank(dueDate) && isBlank(installmentNumber);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
