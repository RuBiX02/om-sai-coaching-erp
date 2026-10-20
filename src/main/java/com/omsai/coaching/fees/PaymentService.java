package com.omsai.coaching.fees;

import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.security.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payment business rules. Payments are computed and validated on the
 * backend against the locked fee-account row:
 *
 * <ul>
 *   <li>the outstanding balance is always server-computed
 *       (net fee minus RECEIVED payments) — never trusted from the
 *       browser;</li>
 *   <li>concurrent submissions serialize on a pessimistic lock of the
 *       fee account, so the balance can never be over-collected;</li>
 *   <li>receipt numbers come from a database sequence (never counting
 *       rows), additionally protected by a unique index;</li>
 *   <li>payments are never edited or deleted; mistakes are corrected
 *       by a voiding workflow that records who, when and why.</li>
 * </ul>
 */
@Service
public class PaymentService {

    public static final String RECEIPT_PREFIX = "OMS-RCP-";

    private final PaymentRepository paymentRepository;
    private final FeeAccountRepository feeAccountRepository;
    private final BusinessClock businessClock;

    public PaymentService(PaymentRepository paymentRepository,
                          FeeAccountRepository feeAccountRepository,
                          BusinessClock businessClock) {
        this.paymentRepository = paymentRepository;
        this.feeAccountRepository = feeAccountRepository;
        this.businessClock = businessClock;
    }

    /** The outstanding balance of a fee account: net fee minus RECEIVED payments. */
    @Transactional(readOnly = true)
    public BigDecimal outstandingOf(FeeAccount account) {
        return account.netFee().subtract(feeAccountRepository.receivedTotal(account.getId()));
    }

    /**
     * Records a payment (full or partial) against a fee account.
     * Locks the account row so two concurrent payments cannot both be
     * accepted against the same balance; rejects overpayments.
     */
    @Transactional
    public Payment record(PaymentForm form, UserAccount receivedByUser) {
        FeeAccount account = feeAccountRepository.getForUpdate(form.getFeeAccountId())
                .orElseThrow(() -> new BusinessRuleException("Fee agreement not found."));
        BigDecimal amount = form.getAmount().setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal outstanding = outstandingOf(account);
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessRuleException(
                    "Payment exceeds the outstanding balance of " + outstanding + ".");
        }
        Payment payment = new Payment();
        payment.setFeeAccount(account);
        payment.setReceiptNumber(nextReceiptNumber());
        payment.setAmount(amount);
        payment.setPaymentDate(form.getPaymentDate());
        payment.setPaymentMethod(form.getPaymentMethod());
        payment.setReferenceNumber(form.getReferenceNumber());
        payment.setRemarks(form.getRemarks());
        payment.setStatus(Payment.Status.RECEIVED);
        payment.setReceivedBy(receivedByUser);
        return paymentRepository.save(payment);
    }

    /**
     * Voids a payment with a mandatory reason (form validation). The
     * voiding is transactional and immediately updates the computed
     * outstanding balance; the voided payment stays visible in the
     * history marked VOIDED. Duplicate voids are rejected.
     */
    @Transactional
    public Payment voidPayment(Long paymentId, String reason, UserAccount voidedByUser) {
        Payment payment = paymentRepository.findWithDetails(paymentId)
                .orElseThrow(() -> new BusinessRuleException("Payment not found."));
        if (payment.isVoided()) {
            throw new BusinessRuleException("This payment has already been voided.");
        }
        // Serialize against concurrent payments on the same account.
        feeAccountRepository.getForUpdate(payment.getFeeAccount().getId());
        payment.setStatus(Payment.Status.VOIDED);
        payment.setVoidedBy(voidedByUser);
        payment.setVoidedAt(businessClock.now());
        payment.setVoidReason(reason.trim());
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment getWithDetails(Long id) {
        return paymentRepository.findWithDetails(id)
                .orElseThrow(() -> new BusinessRuleException("Payment not found."));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Payment> search(String q, LocalDate from, LocalDate to,
                                                                Payment.Method method, Payment.Status status,
                                                                org.springframework.data.domain.Pageable pageable) {
        return paymentRepository.search(q == null ? "" : q.trim().toLowerCase(), from, to, method, status, pageable);
    }

    private String nextReceiptNumber() {
        int year = businessClock.today().getYear();
        return paymentRepository.nextReceiptNumber(RECEIPT_PREFIX + year);
    }
}
