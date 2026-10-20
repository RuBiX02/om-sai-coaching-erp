package com.omsai.coaching.fees;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.security.UserAccount;
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

/**
 * A REAL concurrency test (no rollback transaction): two collectors
 * submit payments against the same balance at the same instant. The
 * pessimistic lock of the fee-account row must serialize them so the
 * account can never be over-collected. Fixtures are committed and then
 * explicitly removed in @AfterEach (these rows are created and deleted
 * by this test only — the application itself never destroys such
 * history).
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceConcurrencyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private FeeAccountService feeAccountService;

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
    private final List<Long> paymentIds = new ArrayList<>();

    @AfterEach
    @SuppressWarnings("unused")
    void removeCommittedFixtures() {
        deleteByIds("payments", paymentIds);
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

    private void deleteByIds(String table, List<Long> ids) {
        for (Long id : ids) {
            try {
                jdbcTemplate.update("delete from " + table + " where id = ?", id);
            } catch (RuntimeException ignored) {
                // Best effort: keep removing the rest even if one delete fails.
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void simultaneousPaymentsCannotOvercollectTheBalance() throws Exception {
        // ---- committed fixture: net fee of exactly 1000.00 -----------------
        Course course = fixtures.course("Concurrency Course");
        courseIds.add(course.getId());
        Batch batch = fixtures.batch(course, "Morning");
        batchIds.add(batch.getId());
        com.omsai.coaching.student.Student student = fixtures.student("Concurrent Student");
        studentIds.add(student.getId());
        Enrollment enrollment = fixtures.activeEnrollment(student, batch);
        enrollmentIds.add(enrollment.getId());
        UserAccount admin = fixtures.adminUser("concurrent-receiver");
        userIds.add(admin.getId());

        FeeAccountForm accountForm = new FeeAccountForm();
        accountForm.setEnrollmentId(enrollment.getId());
        accountForm.setGrossFee(new BigDecimal("1000"));
        FeeAccount account = feeAccountService.create(accountForm, admin);
        accountIds.add(account.getId());

        // ---- two payments of 800 each at the same instant ------------------
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
                        PaymentForm form = new PaymentForm();
                        form.setFeeAccountId(account.getId());
                        form.setAmount(new BigDecimal("800.00"));
                        form.setPaymentDate(LocalDate.of(2026, 9, 30));
                        form.setPaymentMethod(Payment.Method.CASH);
                        return paymentService.record(form, admin);
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

            long payments = outcomes.stream().filter(Payment.class::isInstance).count();
            long rejections = outcomes.stream()
                    .filter(BusinessRuleException.class::isInstance)
                    .map(BusinessRuleException.class::cast)
                    .filter(e -> e.getMessage().contains("Payment exceeds the outstanding balance"))
                    .count();
            long otherFailures = outcomes.size() - payments - rejections;
            assertThat(otherFailures).as("unexpected outcomes: %s", outcomes).isZero();
            assertThat(payments).isEqualTo(1);
            assertThat(rejections).isEqualTo(1);

            Payment accepted = (Payment) outcomes.stream().filter(Payment.class::isInstance).findFirst().orElseThrow();
            paymentIds.add(accepted.getId());
        } finally {
            pool.shutdownNow();
        }

        // Exactly one of the 800.00 payments was collected of the 1000.00 balance.
        BigDecimal outstanding = feeAccountService
                .outstandingOf(feeAccountService.getWithDetails(account.getId()));
        assertThat(outstanding).isEqualByComparingTo(new BigDecimal("200.00"));
    }
}
