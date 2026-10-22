package com.omsai.coaching.fees;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REAL concurrency and rollback tests (no wrapping test transaction):
 * the fixtures are committed and removed again in @AfterEach, so the
 * service really runs in its own transaction and a failure really
 * rolls that transaction back.
 *
 * <p>The pessimistic lock of the fee-account row must serialize
 * concurrent payments, and the creation of an installment plan must
 * never leave a payment unallocated.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class InstallmentConcurrencyTest {

    @Autowired
    private InstallmentService installmentService;

    @Autowired
    private FeeAccountService feeAccountService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentAllocationRepository allocationRepository;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> studentIds = new ArrayList<>();
    private final List<Long> courseIds = new ArrayList<>();
    private final List<Long> subjectIds = new ArrayList<>();
    private final List<Long> batchIds = new ArrayList<>();
    private final List<Long> courseSubjectIds = new ArrayList<>();
    private final List<Long> batchSubjectIds = new ArrayList<>();
    private final List<Long> enrollmentIds = new ArrayList<>();
    private final List<Long> accountIds = new ArrayList<>();
    private final List<Long> installmentIds = new ArrayList<>();
    private final List<Long> paymentIds = new ArrayList<>();

    @AfterEach
    void removeCommittedFixtures() {
        deleteByIds("payment_allocations", allocationIds());
        deleteByIds("payments", paymentIds);
        deleteByIds("fee_installments", installmentIds);
        deleteByIds("fee_accounts", accountIds);
        deleteByIds("enrollments", enrollmentIds);
        deleteByIds("batch_subjects", batchSubjectIds);
        deleteByIds("course_subjects", courseSubjectIds);
        deleteByIds("subjects", subjectIds);
        deleteByIds("batches", batchIds);
        deleteByIds("courses", courseIds);
        deleteByIds("students", studentIds);
        deleteByIds("user_accounts", userIds);
    }

    /** The allocations of this test (they carry no id list of their own). */
    private List<Long> allocationIds() {
        List<Long> ids = new ArrayList<>();
        for (Long accountId : accountIds) {
            ids.addAll(jdbcTemplate.queryForList(
                    "select id from payment_allocations where fee_account_id = ?", Long.class, accountId));
        }
        return ids;
    }

    private void deleteByIds(String table, List<Long> ids) {
        for (Long id : ids) {
            try {
                jdbcTemplate.update("delete from " + table + " where id = ?", id);
            } catch (RuntimeException ignored) {
                // Best effort: keep removing the rest even if one delete fails.
            }
        }
    }

    /** A committed fee account with the given net fee. */
    private FeeAccount account(UserAccount admin, String gross) {
        Course course = fixtures.course("Concurrency Installment Course");
        courseIds.add(course.getId());
        Batch batch = fixtures.batch(course, "Morning");
        batchIds.add(batch.getId());
        Student student = fixtures.student("Concurrency Installment Student");
        studentIds.add(student.getId());
        Enrollment enrollment = fixtures.activeEnrollment(student, batch);
        enrollmentIds.add(enrollment.getId());

        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(enrollment.getId());
        form.setGrossFee(new BigDecimal(gross));
        form.setAcademicYear("2026-27");
        FeeAccount account = feeAccountService.create(form, admin);
        accountIds.add(account.getId());
        return account;
    }

    private void plan(FeeAccount account, UserAccount admin, int count, BigDecimal each) {
        List<InstallmentInput> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(new InstallmentInput(each.toPlainString(),
                    LocalDate.of(2026, 10, 1).plusMonths(i - 1L).toString(), String.valueOf(i)));
        }
        List<FeeInstallment> installments = installmentService.createPlan(account.getId(), rows, admin);
        installmentIds.addAll(installments.stream().map(FeeInstallment::getId).toList());
    }

    private Payment pay(FeeAccount account, UserAccount admin, String amount) {
        PaymentForm form = new PaymentForm();
        form.setFeeAccountId(account.getId());
        form.setAmount(new BigDecimal(amount));
        form.setPaymentDate(LocalDate.of(2026, 10, 5));
        form.setPaymentMethod(Payment.Method.CASH);
        Payment payment = paymentService.record(form, admin);
        paymentIds.add(payment.getId());
        return payment;
    }

    @Test
    void simultaneousPaymentsAgainstAPlanCannotOvercollectOrLeaveAGap() throws Exception {
        UserAccount admin = fixtures.adminUser("concurrency-plan-admin");
        userIds.add(admin.getId());
        FeeAccount account = account(admin, "1000");
        plan(account, admin, 2, new BigDecimal("500"));

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit((Callable<Object>) () -> {
                    ready.countDown();
                    release.await();
                    try {
                        return pay(account, admin, "800");
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            long accepted = outcomes.stream().filter(Payment.class::isInstance).count();
            long rejected = outcomes.stream()
                    .filter(BusinessRuleException.class::isInstance)
                    .map(BusinessRuleException.class::cast)
                    .filter(e -> e.getMessage().contains("exceeds the outstanding balance"))
                    .count();
            assertThat(accepted).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        // The accepted payment is allocated completely, and the account
        // and the installments agree.
        BigDecimal allocated = allocationRepository.findForAccount(account.getId()).stream()
                .map(PaymentAllocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocated).isEqualByComparingTo(new BigDecimal("800"));
        assertThat(feeAccountService.outstandingOf(feeAccountService.getWithDetails(account.getId())))
                .isEqualByComparingTo(new BigDecimal("200"));
        BigDecimal remaining = installmentService.viewFor(account.getId()).stream()
                .map(InstallmentService.InstallmentView::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(remaining).isEqualByComparingTo(new BigDecimal("200"));
    }

    @Test
    void aPlanCreatedWhileAPaymentIsRecordedNeverLeavesThePaymentUnallocated() throws Exception {
        UserAccount admin = fixtures.adminUser("concurrency-plan-race");
        userIds.add(admin.getId());
        FeeAccount account = account(admin, "2000");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            futures.add(pool.submit((Callable<Object>) () -> {
                ready.countDown();
                release.await();
                try {
                    plan(account, admin, 2, new BigDecimal("1000"));
                    return "plan";
                } catch (Exception e) {
                    return e;
                }
            }));
            futures.add(pool.submit((Callable<Object>) () -> {
                ready.countDown();
                release.await();
                try {
                    return pay(account, admin, "500");
                } catch (Exception e) {
                    return e;
                }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();

            for (Future<Object> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // Whatever the order was, the state is complete: a plan exists
        // and the received money is allocated to it - or no plan exists
        // and the payment was simply received.
        List<InstallmentService.InstallmentView> views = installmentService.viewFor(account.getId());
        BigDecimal received = feeAccountService.outstandingOf(feeAccountService.getWithDetails(account.getId()));
        if (views.isEmpty()) {
            assertThat(received).isEqualByComparingTo(new BigDecimal("1500"));
            assertThat(allocationRepository.findForAccount(account.getId())).isEmpty();
        } else {
            BigDecimal allocated = allocationRepository.findForAccount(account.getId()).stream()
                    .map(PaymentAllocation::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(allocated).isEqualByComparingTo(new BigDecimal("500"));
            BigDecimal remaining = views.stream()
                    .map(InstallmentService.InstallmentView::remaining)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(remaining).isEqualByComparingTo(received);
        }
    }

    @Test
    void aPaymentThatCannotBeAllocatedIsRolledBackCompletely() {
        UserAccount admin = fixtures.adminUser("concurrency-rollback");
        userIds.add(admin.getId());
        FeeAccount account = account(admin, "1000");
        plan(account, admin, 2, new BigDecimal("500"));

        // Simulate a corrupted schedule: the installments are smaller
        // than the fee they should cover, so the payment cannot be
        // allocated completely.
        jdbcTemplate.update("update fee_installments set amount = 100 where fee_account_id = ?",
                account.getId());

        assertThatThrownBy(() -> pay(account, admin, "300"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be allocated");

        // The payment transaction was rolled back: no payment and no
        // allocation were written.
        assertThat(paymentIds).isEmpty();
        assertThat(allocationRepository.findForAccount(account.getId())).isEmpty();
        assertThat(feeAccountService.outstandingOf(feeAccountService.getWithDetails(account.getId())))
                .isEqualByComparingTo(new BigDecimal("1000"));
    }

    @Test
    void aConcurrentPaymentAndScheduleEditCannotCorruptTheFinancialRecords() throws Exception {
        UserAccount admin = fixtures.adminUser("concurrency-edit-race");
        userIds.add(admin.getId());
        FeeAccount account = account(admin, "1000");
        plan(account, admin, 2, new BigDecimal("500"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            // One thread replaces the schedule, the other records a
            // payment. Both take the same lock on the fee account, so
            // they serialize.
            futures.add(pool.submit((Callable<Object>) () -> {
                ready.countDown();
                release.await();
                try {
                    List<InstallmentInput> rows = List.of(
                            new InstallmentInput("500", LocalDate.of(2026, 11, 1).toString(), "1"),
                            new InstallmentInput("500", LocalDate.of(2026, 12, 1).toString(), "2"));
                    installmentService.replacePlan(account.getId(), rows, admin);
                    return "edit";
                } catch (Exception e) {
                    return e;
                }
            }));
            futures.add(pool.submit((Callable<Object>) () -> {
                ready.countDown();
                release.await();
                try {
                    return pay(account, admin, "500");
                } catch (Exception e) {
                    return e;
                }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();

            for (Future<Object> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        resyncInstallmentIds(account);

        // Whatever the order was, the financial records are consistent:
        // the payment is allocated completely, the schedule still adds up
        // to the net fee and the two balances agree. Either the payment
        // was booked first and the edit was refused, or the edit was
        // applied first and the payment was allocated to the new rows.
        BigDecimal allocated = allocationRepository.findForAccount(account.getId()).stream()
                .map(PaymentAllocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocated).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(installmentService.scheduledTotal(account.getId()))
                .isEqualByComparingTo(new BigDecimal("1000"));
        BigDecimal remaining = installmentService.viewFor(account.getId()).stream()
                .map(InstallmentService.InstallmentView::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(remaining).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(feeAccountService.outstandingOf(feeAccountService.getWithDetails(account.getId())))
                .isEqualByComparingTo(new BigDecimal("500"));
    }

    /**
     * Re-reads the installment ids of an account so the cleanup also
     * removes the rows a replacement created.
     */
    private void resyncInstallmentIds(FeeAccount account) {
        installmentIds.addAll(jdbcTemplate.queryForList(
                "select id from fee_installments where fee_account_id = ?", Long.class, account.getId()));
    }
}
