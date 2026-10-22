package com.omsai.coaching.fees;

import com.omsai.coaching.common.Auditable;
import com.omsai.coaching.security.UserAccount;
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
import java.time.LocalDate;

/**
 * One scheduled installment of a fee agreement.
 *
 * <p>An installment only <em>schedules</em> the agreed fee: the
 * {@link FeeAccount} stays the authoritative record of what is owed.
 * The sum of the amounts of one account always equals the account's
 * net fee exactly (enforced when the plan is created), and the
 * installment numbers of one account are 1..n.</p>
 *
 * <p>The paid and remaining amounts are never stored: they are
 * derived from the allocations of the account's RECEIVED payments, so
 * a voided payment automatically stops counting without touching the
 * schedule.</p>
 */
@Entity
@Table(name = "fee_installments",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_fee_installments_account_number",
                columnNames = {"fee_account_id", "installment_number"}))
public class FeeInstallment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_account_id", nullable = false)
    private FeeAccount feeAccount;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Digits(integer = 10, fraction = 2)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserAccount createdBy;

    public Long getId() {
        return id;
    }

    public FeeAccount getFeeAccount() {
        return feeAccount;
    }

    void setFeeAccount(FeeAccount feeAccount) {
        this.feeAccount = feeAccount;
    }

    public Integer getInstallmentNumber() {
        return installmentNumber;
    }

    void setInstallmentNumber(Integer installmentNumber) {
        this.installmentNumber = installmentNumber;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public UserAccount getCreatedBy() {
        return createdBy;
    }

    void setCreatedBy(UserAccount createdBy) {
        this.createdBy = createdBy;
    }
}
