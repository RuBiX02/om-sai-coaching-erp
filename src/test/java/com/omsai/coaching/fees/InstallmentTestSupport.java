package com.omsai.coaching.fees;

import com.omsai.coaching.security.UserAccount;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Small helpers for the installment tests: a fee account with a known
 * net fee and the form input of an installment schedule.
 */
final class InstallmentTestSupport {

    private InstallmentTestSupport() {
    }

    /** A fee account with the given gross fee and discount. */
    static FeeAccount account(FeeAccountService feeAccountService,
                               FeeAccountForm form,
                               UserAccount admin) {
        return feeAccountService.create(form, admin);
    }

    /** The form input of one installment. */
    static InstallmentInput row(int number, String amount, LocalDate dueDate) {
        return new InstallmentInput(amount, dueDate.toString(), String.valueOf(number));
    }

    /** A schedule of equal installments due one month apart. */
    static List<InstallmentInput> equalSchedule(BigDecimal netFee, int count, LocalDate firstDueDate) {
        BigDecimal each = netFee.divide(new BigDecimal(count), 2, java.math.RoundingMode.HALF_UP);
        List<InstallmentInput> rows = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 1; i <= count; i++) {
            BigDecimal amount = (i == count) ? netFee.subtract(allocated) : each;
            allocated = allocated.add(amount);
            rows.add(row(i, amount.toPlainString(), firstDueDate.plusMonths(i - 1L)));
        }
        return rows;
    }
}
