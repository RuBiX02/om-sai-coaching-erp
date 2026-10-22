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
 * Editing an existing installment plan.
 *
 * <p>A schedule may be replaced while it carries no payment-allocation
 * history. As soon as a payment has been booked against it, the
 * schedule is part of the financial record: the replacement is refused
 * with an explanation and the original rows stay exactly as they were.
 * This is true even when the payment was voided afterwards, because the
 * allocations of a voided payment are kept as audit history.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InstallmentPlanEditTest {

    @Autowired
    private InstallmentService installmentService;

    @Autowired
    private FeeAccountService feeAccountService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentAllocationRepository allocationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private BusinessClock businessClock;

    private UserAccount admin;
    private FeeAccount account;
    private LocalDate today;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("planedit.admin");
        Course course = fixtures.course("Plan Edit Course");
        Batch batch = fixtures.batch(course, "Morning");
        Student student = fixtures.student("Plan Edit Student");
        Enrollment enrollment = fixtures.activeEnrollment(student, batch);

        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(enrollment.getId());
        form.setGrossFee(new BigDecimal("30000"));
        form.setDiscountAmount(new BigDecimal("2000"));
        form.setAcademicYear("2026-27");
        account = InstallmentTestSupport.account(feeAccountService, form, admin);
        today = businessClock.today();
    }

    /** A three-row plan of 14000/14000/0 is not possible: 30000 net, so 10000 each. */
    private void createPlan() {
        installmentService.createPlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "10000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "10000", today.plusMonths(1)),
                InstallmentTestSupport.row(3, "8000", today.plusMonths(2))), admin);
    }

    private Payment pay(String amount) {
        PaymentForm form = new PaymentForm();
        form.setFeeAccountId(account.getId());
        form.setAmount(new BigDecimal(amount));
        form.setPaymentDate(today);
        form.setPaymentMethod(Payment.Method.CASH);
        return paymentService.record(form, admin);
    }

    private List<InstallmentService.InstallmentView> views() {
        return installmentService.viewFor(account.getId());
    }

    // ----- the happy path ----------------------------------------------------

    @Test
    void anAdministratorCanReplaceAPlanWithoutAllocationHistory() {
        createPlan();

        List<FeeInstallment> replaced = installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", today.plusDays(5)),
                InstallmentTestSupport.row(2, "14000", today.plusMonths(2))), admin);

        assertThat(replaced).hasSize(2);
        assertThat(replaced).extracting(FeeInstallment::getInstallmentNumber)
                .containsExactly(1, 2);
        assertThat(replaced).extracting(FeeInstallment::getAmount)
                .containsExactly(new BigDecimal("14000.00"), new BigDecimal("14000.00"));
        assertThat(views()).hasSize(2);
        assertThat(installmentService.scheduledTotal(account.getId()))
                .isEqualByComparingTo(new BigDecimal("28000"));
    }

    @Test
    void installmentAmountsCanBeCorrected() {
        createPlan();

        installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "12000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "10000", today.plusMonths(1)),
                InstallmentTestSupport.row(3, "6000", today.plusMonths(2))), admin);

        assertThat(views()).extracting(view -> view.installment().getAmount())
                .containsExactly(new BigDecimal("12000.00"), new BigDecimal("10000.00"),
                        new BigDecimal("6000.00"));
    }

    @Test
    void dueDatesCanBeChanged() {
        createPlan();
        LocalDate newFirstDue = today.plusDays(45);

        installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", newFirstDue),
                InstallmentTestSupport.row(2, "14000", today.plusMonths(3))), admin);

        assertThat(views().get(0).installment().getDueDate()).isEqualTo(newFirstDue);
        assertThat(views().get(1).installment().getDueDate()).isEqualTo(today.plusMonths(3));
    }

    @Test
    void installmentsCanBeAdded() {
        createPlan();

        installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "7000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "7000", today.plusMonths(1)),
                InstallmentTestSupport.row(3, "7000", today.plusMonths(2)),
                InstallmentTestSupport.row(4, "7000", today.plusMonths(3))), admin);

        assertThat(views()).hasSize(4);
        assertThat(views()).extracting(view -> view.installment().getInstallmentNumber())
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    void installmentsCanBeRemoved() {
        createPlan();

        installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "14000", today.plusMonths(1))), admin);

        assertThat(views()).hasSize(2);
        assertThat(installmentService.scheduledTotal(account.getId()))
                .isEqualByComparingTo(new BigDecimal("28000"));
    }

    @Test
    void theResultIsAlwaysNumberedSequentiallyFromOne() {
        createPlan();

        installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "9000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "9000", today.plusMonths(1)),
                InstallmentTestSupport.row(3, "10000", today.plusMonths(2))), admin);

        assertThat(views()).extracting(view -> view.installment().getInstallmentNumber())
                .containsExactly(1, 2, 3);
    }

    @Test
    void aRenumberedScheduleKeepsOneRowPerNumber() {
        createPlan();
        List<Long> before = views().stream().map(view -> view.installment().getId()).toList();

        installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "8000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "8000", today.plusMonths(1)),
                InstallmentTestSupport.row(3, "8000", today.plusMonths(2)),
                InstallmentTestSupport.row(4, "4000", today.plusMonths(3))), admin);

        assertThat(views()).hasSize(4);
        // The rows that kept their number kept their identity.
        assertThat(views().get(0).installment().getId()).isEqualTo(before.get(0));
        assertThat(views().get(1).installment().getId()).isEqualTo(before.get(1));
        assertThat(views().get(2).installment().getId()).isEqualTo(before.get(2));
    }

    @Test
    void theFeeAgreementAndItsReceiptsAreUntouched() {
        createPlan();

        installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "14000", today.plusMonths(1))), admin);

        FeeAccount reloaded = feeAccountService.getWithDetails(account.getId());
        assertThat(reloaded.getGrossFee()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(reloaded.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(reloaded.netFee()).isEqualByComparingTo(new BigDecimal("28000"));
        assertThat(feeAccountService.outstandingOf(reloaded)).isEqualByComparingTo(new BigDecimal("28000"));
        // No payment was invented by the edit.
        assertThat(paymentRepository.findForAccount(account.getId())).isEmpty();
    }

    // ----- the validation rules ---------------------------------------------

    @Test
    void theNewScheduleMustEqualTheNetFeeExactly() {
        createPlan();

        assertThatThrownBy(() -> installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "10000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "10000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must add up to exactly the net fee");
    }

    @Test
    void aScheduleWithFewerThanTwoInstallmentsIsRejected() {
        createPlan();

        assertThatThrownBy(() -> installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "28000", today.plusDays(10))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least 2 installments");
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        createPlan();

        assertThatThrownBy(() -> installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "0", today.plusDays(10)),
                InstallmentTestSupport.row(2, "28000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void amountsWithMoreThanTwoDecimalsAreRejected() {
        createPlan();

        assertThatThrownBy(() -> installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000.005", today.plusDays(10)),
                InstallmentTestSupport.row(2, "13999.995", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at most two decimal places");
    }

    @Test
    void aMissingDueDateIsRejected() {
        createPlan();

        assertThatThrownBy(() -> installmentService.replacePlan(account.getId(), List.of(
                new InstallmentInput("14000", "", "1"),
                InstallmentTestSupport.row(2, "14000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("due date is required");
    }

    @Test
    void numberingWithGapsIsRejected() {
        createPlan();

        assertThatThrownBy(() -> installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", today.plusDays(10)),
                InstallmentTestSupport.row(3, "14000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("numbered 1 to 2 without gaps");
    }

    @Test
    void duplicateNumbersAreRejected() {
        createPlan();

        assertThatThrownBy(() -> installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", today.plusDays(10)),
                InstallmentTestSupport.row(1, "14000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("numbered 1 to 2 without gaps");
    }

    @Test
    void anInvalidEditLeavesTheOriginalScheduleCompletelyUnchanged() {
        createPlan();
        List<InstallmentService.InstallmentView> before = views();

        assertThatThrownBy(() -> installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "10000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "10000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class);

        List<InstallmentService.InstallmentView> after = views();
        assertThat(after).hasSameSizeAs(before);
        for (int i = 0; i < before.size(); i++) {
            assertThat(after.get(i).installment().getId()).isEqualTo(before.get(i).installment().getId());
            assertThat(after.get(i).installment().getAmount()).isEqualByComparingTo(
                    before.get(i).installment().getAmount());
            assertThat(after.get(i).installment().getDueDate())
                    .isEqualTo(before.get(i).installment().getDueDate());
        }
        assertThat(installmentService.scheduledTotal(account.getId()))
                .isEqualByComparingTo(new BigDecimal("28000"));
    }

    // ----- the immutability rule ---------------------------------------------

    @Test
    void editingAPlanWithPaymentAllocationsIsRefused() {
        createPlan();
        Payment payment = pay("5000");
        assertThat(allocationRepository.findForAccount(account.getId())).isNotEmpty();

        assertThatThrownBy(() -> installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "14000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be edited because payment-allocation history exists");
    }

    @Test
    void editingAPlanWhosePaymentWasVoidedIsAlsoRefused() {
        createPlan();
        Payment payment = pay("5000");
        paymentService.voidPayment(payment.getId(), "wrong entry", admin);

        // The allocation is kept as history even though the payment was
        // voided, so the schedule is still immutable.
        assertThat(allocationRepository.findForAccount(account.getId())).hasSize(1);

        assertThatThrownBy(() -> installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "14000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be edited because payment-allocation history exists");
    }

    @Test
    void aRefusedEditLeavesThePaymentsTheReceiptsAndTheAllocationsUntouched() {
        createPlan();
        Payment payment = pay("5000");
        String receipt = payment.getReceiptNumber();
        List<PaymentAllocation> allocations = allocationRepository.findForAccount(account.getId());
        List<InstallmentService.InstallmentView> before = views();

        assertThatThrownBy(() -> installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "14000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class);

        // The payment and its receipt are untouched.
        Payment reloaded = paymentService.getWithDetails(payment.getId());
        assertThat(reloaded.getReceiptNumber()).isEqualTo(receipt);
        assertThat(reloaded.getAmount()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(reloaded.isVoided()).isFalse();
        // The allocations are untouched.
        assertThat(allocationRepository.findForAccount(account.getId())).hasSameSizeAs(allocations);
        assertThat(allocationRepository.allocatedTotal(payment.getId()))
                .isEqualByComparingTo(new BigDecimal("5000"));
        // The schedule is untouched.
        assertThat(views()).hasSameSizeAs(before);
        assertThat(views().get(0).installment().getAmount()).isEqualByComparingTo(new BigDecimal("10000.00"));
        // The agreement is untouched.
        assertThat(feeAccountService.getWithDetails(account.getId()).netFee())
                .isEqualByComparingTo(new BigDecimal("28000"));
    }

    @Test
    void aRefusedEditKeepsThePaidAmountOfTheInstallments() {
        createPlan();
        pay("10000");

        assertThatThrownBy(() -> installmentService.replacePlan(account.getId(), List.of(
                InstallmentTestSupport.row(1, "14000", today.plusDays(10)),
                InstallmentTestSupport.row(2, "14000", today.plusMonths(1))), admin))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(views().get(0).paid()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(views().get(0).remaining()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ----- the eligibility flag -----------------------------------------------

    @Test
    void aPlanWithoutAllocationsIsEditable() {
        createPlan();

        assertThat(installmentService.isEditable(account.getId())).isTrue();
    }

    @Test
    void aPlanWithAllocationsIsNotEditable() {
        createPlan();
        pay("5000");

        assertThat(installmentService.isEditable(account.getId())).isFalse();
    }

    @Test
    void anAccountWithoutAPlanIsNotEditable() {
        assertThat(installmentService.isEditable(account.getId())).isFalse();
    }

    @Test
    void theCurrentScheduleCanBeLoadedIntoTheForm() {
        createPlan();

        List<InstallmentInput> schedule = installmentService.scheduleOf(account.getId());

        assertThat(schedule).hasSize(3);
        assertThat(schedule.get(0).installmentNumber()).isEqualTo("1");
        assertThat(schedule.get(0).amount()).isEqualTo("10000.00");
        assertThat(schedule.get(0).dueDate()).isEqualTo(today.plusDays(10).toString());
        assertThat(schedule.get(2).amount()).isEqualTo("8000.00");
    }
}
