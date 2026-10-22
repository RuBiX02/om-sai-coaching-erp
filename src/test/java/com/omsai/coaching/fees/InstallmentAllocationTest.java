package com.omsai.coaching.fees;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentService;
import com.omsai.coaching.enrollment.TransferForm;
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
 * How payments are allocated to an installment plan: the automatic
 * earliest-due-first policy, the effect of voiding, the reconciliation
 * with the fee agreement and the behaviour of a same-course transfer.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InstallmentAllocationTest {

    @Autowired
    private InstallmentService installmentService;

    @Autowired
    private FeeAccountService feeAccountService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private PaymentAllocationRepository allocationRepository;

    @Autowired
    private FeeAccountRepository feeAccountRepository;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private BusinessClock businessClock;

    private UserAccount admin;
    private LocalDate today;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("allocation.admin");
        today = businessClock.today();
    }

    /** A fee account with the given net fee and one student. */
    private FeeAccount account(String gross, String discount, Student student, Batch batch) {
        Enrollment enrollment = fixtures.activeEnrollment(student, batch);
        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(enrollment.getId());
        form.setGrossFee(new BigDecimal(gross));
        form.setDiscountAmount(new BigDecimal(discount));
        form.setAcademicYear("2026-27");
        return feeAccountService.create(form, admin);
    }

    private Payment pay(FeeAccount target, String amount) {
        PaymentForm form = new PaymentForm();
        form.setFeeAccountId(target.getId());
        form.setAmount(new BigDecimal(amount));
        form.setPaymentDate(today);
        form.setPaymentMethod(Payment.Method.CASH);
        return paymentService.record(form, admin);
    }

    // ----- the allocation policy -------------------------------------------

    @Test
    void aPartialPaymentIsAllocatedToTheEarliestInstallment() {
        Course course = fixtures.course("Allocation Course");
        Batch batch = fixtures.batch(course, "Morning");
        Student student = fixtures.student("Allocation Student");
        FeeAccount target = account("30000", "0", student, batch);

        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "10000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "10000", today.plusMonths(1)),
                InstallmentTestSupport.row(3, "10000", today.plusMonths(2))), admin);

        Payment payment = pay(target, "8000");

        List<InstallmentService.InstallmentView> views = installmentService.viewFor(target.getId());
        assertThat(views.get(0).paid()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(views.get(0).remaining()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(views.get(1).paid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(views.get(2).paid()).isEqualByComparingTo(BigDecimal.ZERO);
        // The payment itself is unchanged.
        assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(payment.getReceiptNumber()).startsWith("OMS-RCP-");
        // The allocation adds up to the payment.
        assertThat(allocationRepository.allocatedTotal(payment.getId()))
                .isEqualByComparingTo(new BigDecimal("8000"));
    }

    @Test
    void aPaymentSpanningInstallmentsIsAllocatedOldestDueFirst() {
        Course course = fixtures.course("Spanning Course");
        Batch batch = fixtures.batch(course, "Morning");
        Student student = fixtures.student("Spanning Student");
        FeeAccount target = account("30000", "0", student, batch);

        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "10000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "10000", today.plusMonths(1)),
                InstallmentTestSupport.row(3, "10000", today.plusMonths(2))), admin);

        pay(target, "8000");
        Payment second = pay(target, "5000");

        List<InstallmentService.InstallmentView> views = installmentService.viewFor(target.getId());
        // 2000 finishes the first installment, 3000 goes to the second.
        assertThat(views.get(0).paid()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(views.get(0).remaining()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(views.get(1).paid()).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(views.get(1).remaining()).isEqualByComparingTo(new BigDecimal("7000"));
        assertThat(views.get(2).paid()).isEqualByComparingTo(BigDecimal.ZERO);

        // The fee account and the installments agree: 17000 outstanding.
        assertThat(feeAccountService.outstandingOf(feeAccountService.getWithDetails(target.getId())))
                .isEqualByComparingTo(new BigDecimal("17000"));
        assertThat(remainingOf(views)).isEqualByComparingTo(new BigDecimal("17000"));
        assertThat(allocationRepository.allocatedTotal(second.getId()))
                .isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    void aPaymentCannotExceedTheOutstandingBalance() {
        Course course = fixtures.course("Overpay Course");
        Batch batch = fixtures.batch(course, "Morning");
        Student student = fixtures.student("Overpay Student");
        FeeAccount target = account("10000", "0", student, batch);

        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "5000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "5000", today.plusMonths(1))), admin);

        assertThatThrownBy(() -> pay(target, "10001"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds the outstanding balance");
    }

    @Test
    void aPaymentWithoutAPlanKeepsTheOldBehaviour() {
        Course course = fixtures.course("No Plan Course");
        Batch batch = fixtures.batch(course, "Morning");
        Student student = fixtures.student("No Plan Student");
        FeeAccount target = account("10000", "0", student, batch);

        Payment payment = pay(target, "2500");

        assertThat(allocationRepository.findForAccount(target.getId())).isEmpty();
        assertThat(installmentService.viewFor(target.getId())).isEmpty();
        assertThat(feeAccountService.outstandingOf(feeAccountService.getWithDetails(target.getId())))
                .isEqualByComparingTo(new BigDecimal("7500"));
        assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("2500"));
    }

    // ----- voiding ---------------------------------------------------------

    @Test
    void voidingAPaymentStopsItCountingAndTheBalancesAreRecalculated() {
        Course course = fixtures.course("Void Course");
        Batch batch = fixtures.batch(course, "Morning");
        Student student = fixtures.student("Void Student");
        FeeAccount target = account("10000", "0", student, batch);

        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "6000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "4000", today.plusMonths(1))), admin);

        Payment payment = pay(target, "6000");
        assertThat(installmentService.viewFor(target.getId()).get(0).remaining())
                .isEqualByComparingTo(BigDecimal.ZERO);

        paymentService.voidPayment(payment.getId(), "wrong amount", admin);

        // The allocation is still there (audit history) but does not count.
        assertThat(allocationRepository.findForAccount(target.getId())).hasSize(1);
        InstallmentService.InstallmentView view = installmentService.viewFor(target.getId()).get(0);
        assertThat(view.paid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(view.remaining()).isEqualByComparingTo(new BigDecimal("6000"));
        assertThat(feeAccountService.outstandingOf(feeAccountService.getWithDetails(target.getId())))
                .isEqualByComparingTo(new BigDecimal("10000"));
        // The receipt and the payment record stay.
        assertThat(paymentService.getWithDetails(payment.getId()).getReceiptNumber())
                .isEqualTo(payment.getReceiptNumber());
        assertThat(paymentService.getWithDetails(payment.getId()).isVoided()).isTrue();
    }

    @Test
    void aPaymentAfterAVoidGoesToTheEarliestInstallmentWithABalance() {
        Course course = fixtures.course("After Void Course");
        Batch batch = fixtures.batch(course, "Morning");
        Student student = fixtures.student("After Void Student");
        FeeAccount target = account("20000", "0", student, batch);

        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "10000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "10000", today.plusMonths(1))), admin);

        Payment first = pay(target, "6000");
        paymentService.voidPayment(first.getId(), "wrong amount", admin);
        pay(target, "3000");

        List<InstallmentService.InstallmentView> views = installmentService.viewFor(target.getId());
        assertThat(views.get(0).paid()).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(views.get(1).paid()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ----- reconciliation and transfers -----------------------------------

    @Test
    void theInstallmentBalancesReconcileWithTheAccountBalance() {
        Course course = fixtures.course("Reconcile Course");
        Batch batch = fixtures.batch(course, "Morning");
        Student student = fixtures.student("Reconcile Student");
        FeeAccount target = account("30000", "0", student, batch);

        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "10000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "10000", today.plusMonths(1)),
                InstallmentTestSupport.row(3, "10000", today.plusMonths(2))), admin);
        pay(target, "7500");
        pay(target, "2500");

        BigDecimal accountOutstanding = feeAccountRepository.outstandingOfAccountsWithPlans();
        BigDecimal installmentOutstanding = installmentService.overview().outstandingWithPlans();
        assertThat(installmentOutstanding).isEqualByComparingTo(accountOutstanding);
        assertThat(remainingOf(installmentService.viewFor(target.getId())))
                .isEqualByComparingTo(feeAccountService.outstandingOf(
                        feeAccountService.getWithDetails(target.getId())));
    }

    @Test
    void aSameCourseTransferKeepsThePlanAndItsAllocations() {
        Course course = fixtures.course("Transfer Course");
        Batch morning = fixtures.batch(course, "Morning");
        Batch evening = fixtures.batch(course, "Evening");
        Student student = fixtures.student("Transfer Student");
        FeeAccount target = account("20000", "0", student, morning);

        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "10000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "10000", today.plusMonths(1))), admin);
        Payment payment = pay(target, "4000");

        TransferForm transfer = new TransferForm();
        transfer.setStudentId(student.getId());
        transfer.setTargetBatchId(evening.getId());
        transfer.setTransferDate(today.plusDays(1));
        enrollmentService.transfer(transfer);

        // The same fee agreement, the same plan, the same allocations.
        assertThat(feeAccountRepository.findByStudentIdAndCourseIdAndAcademicYear(
                student.getId(), course.getId(), "2026-27")).isPresent();
        List<InstallmentService.InstallmentView> views = installmentService.viewFor(target.getId());
        assertThat(views).hasSize(2);
        assertThat(views.get(0).paid()).isEqualByComparingTo(new BigDecimal("4000"));
        assertThat(allocationRepository.findForAccount(target.getId())).hasSize(1);
        assertThat(allocationRepository.allocatedTotal(payment.getId()))
                .isEqualByComparingTo(new BigDecimal("4000"));
        assertThat(feeAccountService.outstandingOf(feeAccountService.getWithDetails(target.getId())))
                .isEqualByComparingTo(new BigDecimal("16000"));
    }

    private static BigDecimal remainingOf(List<InstallmentService.InstallmentView> views) {
        return views.stream()
                .map(InstallmentService.InstallmentView::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
