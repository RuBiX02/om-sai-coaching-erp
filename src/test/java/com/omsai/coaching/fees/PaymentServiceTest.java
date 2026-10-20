package com.omsai.coaching.fees;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Payment rules: receipts come from a database sequence, the balance
 * is computed on the server, overpayments are rejected, and voiding
 * records who, when and why while the money history is never erased.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentServiceTest {

    private static final Pattern RECEIPT_FORMAT = Pattern.compile("OMS-RCP-\\d{4}-\\d{6}");

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private FeeAccountService feeAccountService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private Fixtures fixtures;

    private FeeAccount account;
    private UserAccount admin;

    private void buildFixture(String gross, String discount) {
        Course course = fixtures.course("Payments Course");
        Batch batch = fixtures.batch(course, "Morning");
        Enrollment enrollment = fixtures.activeEnrollment(fixtures.student("Paying Student"), batch);
        admin = fixtures.adminUser("receiver");

        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(enrollment.getId());
        form.setGrossFee(new BigDecimal(gross));
        form.setDiscountAmount(new BigDecimal(discount));
        account = feeAccountService.create(form, admin);
    }

    private PaymentForm payment(String amount, LocalDate date) {
        PaymentForm form = new PaymentForm();
        form.setFeeAccountId(account.getId());
        form.setAmount(new BigDecimal(amount));
        form.setPaymentDate(date);
        form.setPaymentMethod(Payment.Method.CASH);
        return form;
    }

    @Test
    void recordsPartialPaymentsAndTracksTheBalance() {
        buildFixture("24000", "4000");

        Payment first = paymentService.record(payment("5000", LocalDate.of(2026, 9, 5)), admin);
        Payment second = paymentService.record(payment("7500.50", LocalDate.of(2026, 9, 20)), admin);

        assertThat(first.getStatus()).isEqualTo(Payment.Status.RECEIVED);
        assertThat(first.getReceivedBy().getId()).isEqualTo(admin.getId());
        assertThat(second.getAmount()).isEqualByComparingTo(new BigDecimal("7500.50"));
        assertThat(feeAccountService.outstandingOf(feeAccountService.getWithDetails(account.getId())))
                .isEqualByComparingTo(new BigDecimal("7499.50"));
    }

    @Test
    void rejectsOverpaymentWithTheComputedBalance() {
        buildFixture("12000", "0");
        paymentService.record(payment("10000", LocalDate.of(2026, 9, 5)), admin);

        assertThatThrownBy(() -> paymentService.record(payment("3000", LocalDate.of(2026, 9, 6)), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Payment exceeds the outstanding balance of 2000")
                .hasMessageContaining(".");

        // The failed attempt left nothing behind.
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(feeAccountService.outstandingOf(feeAccountService.getWithDetails(account.getId())))
                .isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    void voidingRecordsWhoWhenAndWhyAndRestoresTheBalance() {
        buildFixture("12000", "0");
        Payment received = paymentService.record(payment("4000", LocalDate.of(2026, 9, 5)), admin);

        Payment voided = paymentService.voidPayment(received.getId(), "Cash mistakenly handed to wrong batch.", admin);

        assertThat(voided.getStatus()).isEqualTo(Payment.Status.VOIDED);
        assertThat(voided.getVoidedBy().getId()).isEqualTo(admin.getId());
        assertThat(voided.getVoidedAt()).isNotNull();
        assertThat(voided.getVoidReason()).isEqualTo("Cash mistakenly handed to wrong batch.");
        assertThat(paymentRepository.count()).isEqualTo(1); // the row remains, never deleted
        assertThat(feeAccountService.outstandingOf(feeAccountService.getWithDetails(account.getId())))
                .isEqualByComparingTo(new BigDecimal("12000.00"));
    }

    @Test
    void duplicateVoidsAreRejected() {
        buildFixture("12000", "0");
        Payment received = paymentService.record(payment("4000", LocalDate.of(2026, 9, 5)), admin);
        paymentService.voidPayment(received.getId(), "Duplicate entry.", admin);

        assertThatThrownBy(() -> paymentService.voidPayment(received.getId(), "Another reason.", admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("This payment has already been voided");
    }

    @Test
    void receiptNumbersComeFromASequenceNotFromCountingRows() {
        buildFixture("100000", "0");

        Payment first = paymentService.record(payment("1000", LocalDate.of(2026, 9, 5)), admin);
        assertThat(first.getReceiptNumber()).matches("OMS-RCP-\\d{4}-\\d{6}");

        // Void the first payment and record another: even though the number
        // of RECEIVED rows returns to zero between the two, the second
        // receipt number stays strictly ahead of the first (a sequence).
        paymentService.voidPayment(first.getId(), "Wrong amount entered.", admin);
        Payment second = paymentService.record(payment("1000", LocalDate.of(2026, 9, 6)), admin);

        assertThat(second.getReceiptNumber()).matches("OMS-RCP-\\d{4}-\\d{6}");
        assertThat(second.getReceiptNumber()).isNotEqualTo(first.getReceiptNumber());
        long firstNo = Long.parseLong(first.getReceiptNumber().substring(first.getReceiptNumber().length() - 6));
        long secondNo = Long.parseLong(second.getReceiptNumber().substring(second.getReceiptNumber().length() - 6));
        assertThat(secondNo).isGreaterThan(firstNo);

        // Consecutive payments also never share a receipt number.
        List<Payment> all = paymentRepository.findAll();
        assertThat(all.size()).isEqualTo(2);
        assertThat(all.stream().map(Payment::getReceiptNumber).distinct().count()).isEqualTo(2);
    }
}
