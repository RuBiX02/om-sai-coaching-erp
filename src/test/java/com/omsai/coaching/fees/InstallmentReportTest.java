package com.omsai.coaching.fees;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The derived status of an installment and the management reports.
 * Every date is relative to the business date, so the tests do not
 * depend on the day they run.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InstallmentReportTest {

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
    private LocalDate today;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("report.admin");
        today = businessClock.today();
    }

    private FeeAccount account(String gross, String discount, String studentName) {
        Course course = fixtures.course("Report Installment Course");
        Batch batch = fixtures.batch(course, "Morning");
        Student student = fixtures.student(studentName);
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

    // ----- status ----------------------------------------------------------

    @Test
    void aFullyPaidInstallmentIsPaid() {
        FeeAccount target = account("10000", "0", "Status Paid Student");
        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "6000", today.plusDays(5)),
                InstallmentTestSupport.row(2, "4000", today.plusMonths(1))), admin);
        pay(target, "10000");

        List<InstallmentService.InstallmentView> views = installmentService.viewFor(target.getId());
        assertThat(views.get(0).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(views.get(0).remaining()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(views.get(1).status()).isEqualTo(InstallmentStatus.PAID);
    }

    @Test
    void aPartlyPaidInstallmentIsPartiallyPaid() {
        FeeAccount target = account("10000", "0", "Status Partial Student");
        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "6000", today.plusDays(5)),
                InstallmentTestSupport.row(2, "4000", today.plusMonths(1))), admin);
        pay(target, "2000");

        List<InstallmentService.InstallmentView> views = installmentService.viewFor(target.getId());
        assertThat(views.get(0).status()).isEqualTo(InstallmentStatus.PARTIALLY_PAID);
        assertThat(views.get(0).paid()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(views.get(0).remaining()).isEqualByComparingTo(new BigDecimal("4000"));
        // The second installment was not touched at all.
        assertThat(views.get(1).status()).isEqualTo(InstallmentStatus.UPCOMING);
    }

    @Test
    void anInstallmentWithAPastDueDateAndABalanceIsOverdue() {
        FeeAccount target = account("10000", "0", "Status Overdue Student");
        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "6000", today.minusDays(10)),
                InstallmentTestSupport.row(2, "4000", today.plusMonths(1))), admin);

        List<InstallmentService.InstallmentView> views = installmentService.viewFor(target.getId());
        assertThat(views.get(0).status()).isEqualTo(InstallmentStatus.OVERDUE);
        assertThat(views.get(0).remaining()).isEqualByComparingTo(new BigDecimal("6000"));
        assertThat(views.get(1).status()).isEqualTo(InstallmentStatus.UPCOMING);
    }

    @Test
    void anOverdueInstallmentCanAlsoBePartlyPaidAndStillShowsTheBalance() {
        FeeAccount target = account("10000", "0", "Status Overdue Paid Student");
        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "6000", today.minusDays(10)),
                InstallmentTestSupport.row(2, "4000", today.plusMonths(1))), admin);
        pay(target, "2000");

        InstallmentService.InstallmentView view = installmentService.viewFor(target.getId()).get(0);
        assertThat(view.status()).isEqualTo(InstallmentStatus.OVERDUE);
        assertThat(view.paid()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(view.remaining()).isEqualByComparingTo(new BigDecimal("4000"));
    }

    @Test
    void anInstallmentDueTodayIsClassifiedAsDueToday() {
        FeeAccount target = account("10000", "0", "Status Today Student");
        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "6000", today),
                InstallmentTestSupport.row(2, "4000", today.plusMonths(1))), admin);

        List<InstallmentService.InstallmentView> views = installmentService.viewFor(target.getId());
        assertThat(views.get(0).status()).isEqualTo(InstallmentStatus.DUE_TODAY);
        assertThat(views.get(1).status()).isEqualTo(InstallmentStatus.UPCOMING);
    }

    @Test
    void theStatusPrecedenceIsDeterministic() {
        // PAID wins over everything, then OVERDUE, then PARTIALLY_PAID,
        // then DUE_TODAY, then UPCOMING.
        assertThat(InstallmentStatus.of(today.minusDays(5), new BigDecimal("100"),
                new BigDecimal("100"), today)).isEqualTo(InstallmentStatus.PAID);
        assertThat(InstallmentStatus.of(today.minusDays(5), new BigDecimal("100"),
                new BigDecimal("40"), today)).isEqualTo(InstallmentStatus.OVERDUE);
        assertThat(InstallmentStatus.of(today.plusDays(5), new BigDecimal("100"),
                new BigDecimal("40"), today)).isEqualTo(InstallmentStatus.PARTIALLY_PAID);
        assertThat(InstallmentStatus.of(today, new BigDecimal("100"),
                BigDecimal.ZERO, today)).isEqualTo(InstallmentStatus.DUE_TODAY);
        assertThat(InstallmentStatus.of(today.plusDays(5), new BigDecimal("100"),
                BigDecimal.ZERO, today)).isEqualTo(InstallmentStatus.UPCOMING);
    }

    @Test
    void theBalancesAreCalculatedWithExactDecimals() {
        FeeAccount target = account("30000", "0.01", "Status Exact Student");
        // 30000.00 - 0.01 = 29999.99 exactly.
        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "14999.99", today.plusDays(5)),
                InstallmentTestSupport.row(2, "15000.00", today.plusMonths(1))), admin);
        pay(target, "0.01");

        List<InstallmentService.InstallmentView> views = installmentService.viewFor(target.getId());
        assertThat(views.get(0).paid()).isEqualByComparingTo(new BigDecimal("0.01"));
        assertThat(views.get(0).remaining()).isEqualByComparingTo(new BigDecimal("14999.98"));
        assertThat(feeAccountService.outstandingOf(feeAccountService.getWithDetails(target.getId())))
                .isEqualByComparingTo(new BigDecimal("29999.98"));
    }

    // ----- reports ---------------------------------------------------------

    @Test
    void theUpcomingReportRespectsTheSelectedDates() {
        FeeAccount target = account("30000", "0", "Report Upcoming Student");
        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "10000", today.plusDays(5)),
                InstallmentTestSupport.row(2, "10000", today.plusDays(40)),
                InstallmentTestSupport.row(3, "10000", today.plusDays(80))), admin);

        var page = installmentService.upcoming(today, today.plusDays(30), null, null, null,
                PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getInstallmentNumber()).isEqualTo(1);
        assertThat(page.getContent().get(0).getDueDate()).isEqualTo(today.plusDays(5));

        var later = installmentService.upcoming(today.plusDays(60), today.plusDays(120), null, null, null,
                PageRequest.of(0, 20));
        assertThat(later.getTotalElements()).isEqualTo(1);
        assertThat(later.getContent().get(0).getInstallmentNumber()).isEqualTo(3);
    }

    @Test
    void theUpcomingReportCanBeFilteredByStudent() {
        FeeAccount target = account("10000", "0", "Report Filter Student");
        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "5000", today.plusDays(5)),
                InstallmentTestSupport.row(2, "5000", today.plusDays(40))), admin);

        var found = installmentService.upcoming(today, today.plusDays(30), "report filter",
                null, null, PageRequest.of(0, 20));
        assertThat(found.getTotalElements()).isEqualTo(1);
        assertThat(found.getContent().get(0).getStudentName()).contains("Report Filter Student");
        var notFound = installmentService.upcoming(today, today.plusDays(30), "nobody",
                null, null, PageRequest.of(0, 20));
        assertThat(notFound.getTotalElements()).isZero();
    }

    @Test
    void theOverdueReportExcludesPaidAndVoidedOnlyBalances() {
        FeeAccount target = account("20000", "0", "Report Overdue Student");
        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "10000", today.minusDays(10)),
                InstallmentTestSupport.row(2, "10000", today.minusDays(5))), admin);

        // The first installment is paid in full, the second one gets a
        // payment that is voided again.
        pay(target, "10000");
        Payment voided = pay(target, "5000");
        paymentService.voidPayment(voided.getId(), "wrong entry", admin);

        var overdue = installmentService.overdue(null, null, null, PageRequest.of(0, 20));
        // Only the second installment still has a balance.
        assertThat(overdue.getTotalElements()).isEqualTo(1);
        InstallmentRow row = overdue.getContent().get(0);
        assertThat(row.getInstallmentNumber()).isEqualTo(2);
        assertThat(row.getPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.getAmount().subtract(row.getPaid())).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(row.getDaysOverdue()).isEqualTo(5);
    }

    @Test
    void theDashboardFiguresComeFromTheDatabase() {
        FeeAccount target = account("30000", "0", "Report Dashboard Student");
        installmentService.createPlan(target.getId(), List.of(
                InstallmentTestSupport.row(1, "10000", today.minusDays(5)),
                InstallmentTestSupport.row(2, "10000", today.plusDays(3)),
                InstallmentTestSupport.row(3, "10000", today.plusDays(40))), admin);

        var overview = installmentService.overview();
        assertThat(overview.planCount()).isEqualTo(1);
        assertThat(overview.overdueCount()).isEqualTo(1);
        assertThat(overview.overdueBalance()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(overview.dueInSevenDays()).isEqualTo(1);
        assertThat(overview.dueInSevenDaysAmount()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(overview.nextDueDate()).isEqualTo(today.plusDays(3));
        // The installment balances are the same money as the account's
        // outstanding balance - never added to it.
        assertThat(overview.outstandingWithPlans())
                .isEqualByComparingTo(feeAccountService.outstandingOf(
                        feeAccountService.getWithDetails(target.getId())));
    }
}
