package com.omsai.coaching.fees;

import com.omsai.coaching.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/**
 * The part of one payment that covers one installment.
 *
 * <p>Allocations are append-only history. When a payment is voided,
 * its allocations are NOT deleted: they stay visible so the audit
 * trail of "this money was booked against that installment" survives.
 * Only allocations whose payment is currently {@code RECEIVED} count
 * towards the effective paid amount - a voided payment contributes
 * zero, and the installment balances are derived again from the
 * remaining data.</p>
 *
 * <p>The database proves that the payment and the installment belong
 * to the same fee account (composite foreign keys).</p>
 */
@Entity
@Table(name = "payment_allocations",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_payment_allocations_payment_installment",
                columnNames = {"payment_id", "installment_id"}))
public class PaymentAllocation extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installment_id", nullable = false)
    private FeeInstallment installment;

    /** The fee account both the payment and the installment belong to. */
    @Column(name = "fee_account_id", nullable = false)
    private Long feeAccountId;

    @Digits(integer = 10, fraction = 2)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    public Long getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
    }

    void setPayment(Payment payment) {
        this.payment = payment;
    }

    public FeeInstallment getInstallment() {
        return installment;
    }

    void setInstallment(FeeInstallment installment) {
        this.installment = installment;
    }

    public Long getFeeAccountId() {
        return feeAccountId;
    }

    void setFeeAccountId(Long feeAccountId) {
        this.feeAccountId = feeAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
