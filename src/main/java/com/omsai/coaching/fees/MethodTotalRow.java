package com.omsai.coaching.fees;

/**
 * Projection row of collections per payment method.
 */
public interface MethodTotalRow {

    String getMethod();

    long getPaymentCount();

    java.math.BigDecimal getTotal();
}
