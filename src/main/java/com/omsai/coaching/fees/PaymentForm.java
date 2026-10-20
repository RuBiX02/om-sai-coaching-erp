package com.omsai.coaching.fees;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Form backing the record-a-payment page. The outstanding balance is
 * NOT a form field: it is computed on the backend from the fee account
 * and its RECEIVED payments.
 */
public class PaymentForm {

    /**
     * Hidden field linking to the fee account; protected by server-side
     * computation and locking, never by UI hiding alone.
     */
    @NotNull(message = "Choose a fee agreement.")
    private Long feeAccountId;

    @NotNull(message = "Payment amount is required.")
    @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero.")
    @Digits(integer = 10, fraction = 2, message = "Amount can have at most 10 digits and 2 decimals.")
    private BigDecimal amount;

    @NotNull(message = "Payment date is required.")
    private LocalDate paymentDate;

    @NotNull(message = "Choose a payment method.")
    private Payment.Method paymentMethod;

    @Size(max = 80, message = "Reference can be at most 80 characters.")
    private String referenceNumber;

    @Size(max = 2000, message = "Remarks can be at most 2000 characters.")
    private String remarks;

    public Long getFeeAccountId() {
        return feeAccountId;
    }

    public void setFeeAccountId(Long feeAccountId) {
        this.feeAccountId = feeAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public Payment.Method getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(Payment.Method paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
