package com.omsai.coaching.fees;

import com.omsai.coaching.common.Auditable;
import com.omsai.coaching.security.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.Instant;
import java.time.LocalDate;

/**
 * A payment recorded against a fee agreement. Payments are
 * append-only: they are never edited or deleted. An incorrectly
 * recorded payment is corrected by voiding it, which records who,
 * when and why. Amounts use NUMERIC(12,2) — never floats.
 */
@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_payments_receipt_number",
                columnNames = {"receipt_number"}))
public class Payment extends Auditable {

    public enum Method {
        CASH,
        UPI,
        BANK_TRANSFER,
        CARD,
        CHEQUE,
        OTHER
    }

    public enum Status {
        RECEIVED,
        VOIDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_account_id", nullable = false)
    private FeeAccount feeAccount;

    @Column(name = "receipt_number", nullable = false, length = 40)
    private String receiptNumber;

    @Digits(integer = 10, fraction = 2)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private Method paymentMethod;

    @Column(name = "reference_number", length = 80)
    private String referenceNumber;

    @Column(columnDefinition = "text")
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.RECEIVED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "received_by", nullable = false)
    private UserAccount receivedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voided_by")
    private UserAccount voidedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    public Long getId() {
        return id;
    }

    public FeeAccount getFeeAccount() {
        return feeAccount;
    }

    void setFeeAccount(FeeAccount feeAccount) {
        this.feeAccount = feeAccount;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public Method getPaymentMethod() {
        return paymentMethod;
    }

    void setPaymentMethod(Method paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public String getRemarks() {
        return remarks;
    }

    void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Status getStatus() {
        return status;
    }

    void setStatus(Status status) {
        this.status = status;
    }

    public UserAccount getReceivedBy() {
        return receivedBy;
    }

    void setReceivedBy(UserAccount receivedBy) {
        this.receivedBy = receivedBy;
    }

    public UserAccount getVoidedBy() {
        return voidedBy;
    }

    void setVoidedBy(UserAccount voidedBy) {
        this.voidedBy = voidedBy;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    void setVoidedAt(Instant voidedAt) {
        this.voidedAt = voidedAt;
    }

    public String getVoidReason() {
        return voidReason;
    }

    void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
    }

    public boolean isReceived() {
        return status == Status.RECEIVED;
    }

    public boolean isVoided() {
        return status == Status.VOIDED;
    }
}
