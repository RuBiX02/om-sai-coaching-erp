package com.omsai.coaching.common;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Display formatting for monetary values.
 *
 * <p>The formatting is presentation only: the stored and calculated
 * values stay exact {@code NUMERIC(12,2)} numbers, and nothing is
 * rounded on the way in or out of the database. A monetary value is
 * shown with the rupee sign and exactly two decimal places, so
 * "12000", "12000.0" and "12000.00" all read the same way.</p>
 */
@Component("money")
public class MoneyFormat {

    /** The dash used for a value that does not exist. */
    private static final String NONE = "–";

    /**
     * A non-breaking space keeps the rupee sign attached to its amount,
     * so a narrow table column never leaves the symbol alone on one
     * line. It renders exactly like the normal {@code ₹ 12000.00} form.
     */
    private static final String NBSP = "\u00A0";

    /** Formats a monetary value, e.g. {@code ₹ 12000.00}. */
    public String money(Object value) {
        if (value == null) {
            return NONE;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return value.toString();
        }
        return "₹" + NBSP + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Formats a monetary value without the sign, e.g. {@code 12000.00}. */
    public String amount(Object value) {
        if (value == null) {
            return NONE;
        }
        try {
            return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException e) {
            return value.toString();
        }
    }
}
