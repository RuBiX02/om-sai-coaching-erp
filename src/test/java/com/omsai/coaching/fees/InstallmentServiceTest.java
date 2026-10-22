package com.omsai.coaching.fees;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules of an installment plan: a valid schedule is accepted and
 * changes nothing but the schedule itself, an invalid one is rejected
 * with a precise message and leaves no rows behind, and payments that
 * were already received are allocated to the new schedule.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InstallmentServiceTest {

    @Autowired
    private InstallmentService installmentService;

    @Autowired
    private FeeAccountService feeAccountService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private BusinessClock businessClock;

    private UserAccount admin;
    private FeeAccount account;
    private LocalDate today;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("installment.admin");
        Course course = fixtures.course("Installment Course");
        Batch batch = fixtures.batch(course, "Morning");
        Student student = fixtures.student("Installment Student");
        Enrollment enrollment = fixtures.activeEnrollment(student, batch);

        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(enrollment.getId());
        form.setGrossFee(new BigDecimal("30000"));
        form.setDiscountAmount(new BigDecimal("2000"));
        form.setAcademicYear("2026-27");
        account = InstallmentTestSupport.account(feeAccountService, form, admin);
        today = businessClock.today();
    }

    private Payment pay(String amount) {
        PaymentForm form = new PaymentForm();
        form.setFeeAccountId(account.getId());
        form.setAmount(new BigDecimal(amount));
        form.setPaymentDate(today);
        form.setPaymentMethod(Payment.Method.CASH);
        return paymentService.record(form, admin);
    }

    // ----- creating a plan --------------------------------------------------

    @Test
    void administratorCreatesAValidPlan() {
        List<InstallmentInput> rows = List.of(
                InstallmentTestSupport.row(1, "10000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "10000", today.plusMonths(1)),
                InstallmentTestSupport.row(3, "8000", today.plusMonths(2)));

        List<FeeInstallment> installments = installmentService.createPlan(account.getId(), rows, admin);

        assertThat(installments).hasSize(3);
        assertThat(installments).extracting(FeeInstallment::getInstallmentNumber)
                .containsExactly(1, 2, 3);
        assertThat(installments).extracting(FeeInstallment::getAmount)
                .containsExactly(new BigDecimal("10000.00"), new BigDecimal("10000.00"), new BigDecimal("8000.00"));
        assertThat(installments).allSatisfy(installment -> {
            assertThat(installment.getFeeAccount().getId()).isEqualTo(account.getId());
            assertThat(installment.getDueDate()).isNotNull();
        });
        // The fee agreement itself is untouched: a plan schedules the
        // agreed fee, it never changes it.
        FeeAccount reloaded = feeAccountService.getWithDetails(account.getId());
        assertThat(reloaded.getGrossFee()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(reloaded.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(reloaded.netFee()).isEqualByComparingTo(new BigDecimal("28000"));
        assertThat(feeAccountService.outstandingOf(reloaded)).isEqualByComparingTo(new BigDecimal("28000"));
    }

    @Test
    void aScheduleNeedsAtLeastTwoInstallments() {
        List<InstallmentInput> rows = List.of(
                InstallmentTestSupport.row(1, "28000", today.plusDays(10)));

        assertThatThrownBy(() -> installmentService.createPlan(account.getId(), rows, admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least 2 installments");
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        assertThatThrownBy(() -> installmentService.createPlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "0", today.plusDays(10)),
                InstallmentTestSupport.row(2, "28000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than zero");

        assertThatThrownBy(() -> installmentService.createPlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "-100", today.plusDays(10)),
                InstallmentTestSupport.row(2, "28100", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void amountsWithMoreThanTwoDecimalsAreRejected() {
        assertThatThrownBy(() -> installmentService.createPlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000.005", today.plusDays(10)),
                InstallmentTestSupport.row(2, "13999.995", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at most two decimal places");
    }

    @Test
    void theScheduledTotalMustEqualTheNetFee() {
        assertThatThrownBy(() -> installmentService.createPlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "10000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "10000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must add up to exactly the net fee");

        // The other direction as well: too much is not accepted either.
        assertThatThrownBy(() -> installmentService.createPlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "15000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "15000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must add up to exactly the net fee");
    }

    @Test
    void aSecondPlanIsRejected() {
        installmentService.createPlan(account.getId(),
                InstallmentTestSupport.equalSchedule(account.netFee(), 2, today.plusDays(10)), admin);

        assertThatThrownBy(() -> installmentService.createPlan(account.getId(),
                InstallmentTestSupport.equalSchedule(account.netFee(), 2, today.plusDays(20)), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has an installment plan");
    }

    @Test
    void installmentNumbersMustBeUniqueAndSequential() {
        assertThatThrownBy(() -> installmentService.createPlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", today.plusDays(10)),
                InstallmentTestSupport.row(1, "14000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("numbered 1 to 2 without gaps");

        assertThatThrownBy(() -> installmentService.createPlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", today.plusDays(10)),
                InstallmentTestSupport.row(3, "14000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("numbered 1 to 2 without gaps");
    }

    @Test
    void aPlanBelongsToTheSelectedFeeAccount() {
        installmentService.createPlan(account.getId(),
                InstallmentTestSupport.equalSchedule(account.netFee(), 2, today.plusDays(10)), admin);

        List<InstallmentService.InstallmentView> views = installmentService.viewFor(account.getId());
        assertThat(views).hasSize(2);
        assertThat(views).allSatisfy(view ->
                assertThat(view.installment().getFeeAccount().getId()).isEqualTo(account.getId()));
        // Another account has no plan and no installments.
        assertThat(installmentService.viewFor(account.getId() + 9999)).isEmpty();
    }

    // ----- payments that were already received -----------------------------

    @Test
    void existingPaymentsAreAllocatedWhenThePlanIsCreated() {
        pay("10000");
        pay("5000");

        installmentService.createPlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "14000", today.plusMonths(1))), admin);

        List<InstallmentService.InstallmentView> views = installmentService.viewFor(account.getId());
        assertThat(views).hasSize(2);
        // The first payment fills the first installment, the second one
        // continues there and then spills into the second installment.
        assertThat(views.get(0).paid()).isEqualByComparingTo(new BigDecimal("14000"));
        assertThat(views.get(0).remaining()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(views.get(1).paid()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(views.get(1).remaining()).isEqualByComparingTo(new BigDecimal("13000"));
        // The payments themselves are untouched.
        assertThat(feeAccountService.outstandingOf(feeAccountService.getWithDetails(account.getId())))
                .isEqualByComparingTo(new BigDecimal("13000"));
    }

    @Test
    void voidedPaymentsAreNotAllocated() {
        Payment payment = pay("10000");
        paymentService.voidPayment(payment.getId(), "wrong entry", admin);

        installmentService.createPlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "14000", today.plusMonths(1))), admin);

        List<InstallmentService.InstallmentView> views = installmentService.viewFor(account.getId());
        assertThat(views.get(0).paid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(views.get(0).remaining()).isEqualByComparingTo(new BigDecimal("14000"));
    }

    @Test
    void aFullyPaidAccountCanBeScheduledWithoutInventingPayments() {
        pay("28000");

        installmentService.createPlan(account.getId(),
                InstallmentTestSupport.equalSchedule(account.netFee(), 2, today.plusDays(10)), admin);

        List<InstallmentService.InstallmentView> views = installmentService.viewFor(account.getId());
        assertThat(views.get(0).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(views.get(0).remaining()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(views.get(1).status()).isEqualTo(InstallmentStatus.PAID);
        // Still exactly one payment - the plan did not create another.
        assertThat(paymentService.search("", null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void anInvalidPlanLeavesNoRowsBehind() {
        assertThatThrownBy(() -> installmentService.createPlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "10000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "10000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(installmentService.viewFor(account.getId())).isEmpty();
        assertThat(installmentService.hasPlan(account.getId())).isFalse();
    }
}
