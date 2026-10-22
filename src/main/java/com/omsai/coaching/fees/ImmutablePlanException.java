package com.omsai.coaching.fees;

import com.omsai.coaching.common.BusinessRuleException;

/**
 * Thrown when an installment schedule cannot be changed because it
 * already carries payment-allocation history.
 *
 * <p>It is a {@link BusinessRuleException} like every other rule
 * violation, but it has its own type so the controller can tell an
 * immutable schedule (nothing the administrator typed can help - the
 * plan is part of the financial record) apart from an invalid
 * submission (the form can be corrected and submitted again).</p>
 */
public class ImmutablePlanException extends BusinessRuleException {

    public ImmutablePlanException(String message) {
        super(message);
    }
}
