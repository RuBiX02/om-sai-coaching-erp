package com.omsai.coaching.fees;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.enrollment.EnrollmentService;
import com.omsai.coaching.enrollment.TransferForm;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fee-agreement rules: one agreement per student + course + academic
 * year (never a second charge for the same course), amounts in
 * BigDecimal with the net fee computed server side, the due-date and
 * notes-only edit lock once payments exist, and the transfer flow that
 * re-points the agreement instead of creating a second one.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeeAccountServiceTest {

    @Autowired
    private FeeAccountService feeAccountService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private FeeAccountRepository feeAccountRepository;

    @Autowired
    private Fixtures fixtures;

    private Course course;
    private Batch morningBatch;
    private Batch eveningBatch;
    private UserAccount admin;

    private void buildFixture() {
        course = fixtures.course("Physics Course");
        morningBatch = fixtures.batch(course, "Morning");
        eveningBatch = fixtures.batch(course, "Morning");
        admin = fixtures.adminUser("fees");
    }

    private FeeAccountForm form(Long enrollmentId, String gross, String discount) {
        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(enrollmentId);
        form.setGrossFee(new BigDecimal(gross));
        if (discount != null) {
            form.setDiscountAmount(new BigDecimal(discount));
        }
        return form;
    }

    private Enrollment enrolled(com.omsai.coaching.student.Student student, Batch batch) {
        Enrollment enrollment = fixtures.activeEnrollment(student, batch);
        // Detach so later mutations in the test do not leak into asserts.
        enrollmentRepository.flush();
        return enrollment;
    }

    @Test
    void createsAgreementFromTheEnrollmentWithServerComputedNetFee() {
        buildFixture();
        Enrollment enrollment = enrolled(fixtures.student("Fee Student"), morningBatch);

        FeeAccountForm form = form(enrollment.getId(), "24000", "4000");

        FeeAccount account = feeAccountService.create(form, admin);

        assertThat(account.getId()).isNotNull();
        assertThat(account.getStudent().getId()).isEqualTo(enrollment.getStudent().getId());
        assertThat(account.getCourse().getId()).isEqualTo(course.getId());
        assertThat(account.getEnrollment().getId()).isEqualTo(enrollment.getId());
        // Academic year defaults to the batch's year ("2026-27" from the fixture).
        assertThat(account.getAcademicYear()).isEqualTo("2026-27");
        assertThat(account.netFee()).isEqualByComparingTo(new BigDecimal("20000.00"));
        assertThat(feeAccountService.outstandingOf(account)).isEqualByComparingTo(new BigDecimal("20000.00"));
    }

    @Test
    void rejectsDuplicateAgreementForSameStudentCourseAndYear() {
        buildFixture();
        Enrollment enrollment = enrolled(fixtures.student("Duplicate Student"), morningBatch);
        feeAccountService.create(form(enrollment.getId(), "24000", null), admin);

        assertThatThrownBy(() -> feeAccountService.create(form(enrollment.getId(), "25000", null), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has a fee agreement for");
    }

    @Test
    void sameStudentMayHaveSeparateAgreementsInDifferentAcademicYears() {
        buildFixture();
        Enrollment enrollment = enrolled(fixtures.student("Multiyear Student"), morningBatch);
        feeAccountService.create(form(enrollment.getId(), "24000", null), admin);

        FeeAccountForm nextYear = form(enrollment.getId(), "24000", null);
        nextYear.setAcademicYear("2027-28");
        FeeAccount second = feeAccountService.create(nextYear, admin);

        assertThat(second.getId()).isNotNull();
        assertThat(second.getAcademicYear()).isEqualTo("2027-28");
    }

    @Test
    void discountCannotExceedGrossFee() {
        buildFixture();
        Enrollment enrollment = enrolled(fixtures.student("Discount Student"), morningBatch);

        assertThatThrownBy(() -> feeAccountService.create(form(enrollment.getId(), "1000", "1500"), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Discount cannot exceed the gross fee");
    }

    @Test
    void academicYearFormatIsValidated() {
        buildFixture();
        Enrollment enrollment = enrolled(fixtures.student("Year Student"), morningBatch);

        FeeAccountForm badYear = form(enrollment.getId(), "24000", null);
        badYear.setAcademicYear("2026/27");
        assertThatThrownBy(() -> feeAccountService.create(badYear, admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must use the format");
    }

    @Test
    void agreementNeedsAnActiveEnrollment() {
        buildFixture();
        Enrollment enrollment = enrolled(fixtures.student("Closed Student"), morningBatch);
        Enrollment managed = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        managed.setStatus(Enrollment.Status.WITHDRAWN);
        managed.setEndDate(LocalDate.of(2026, 9, 2));

        assertThatThrownBy(() -> feeAccountService.create(form(enrollment.getId(), "24000", null), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("can only be created for an ACTIVE enrollment");
    }

    @Test
    void oncePaymentsExistOnlyDueDateAndNotesRemainEditable() {
        buildFixture();
        Enrollment enrollment = enrolled(fixtures.student("Locked Student"), morningBatch);
        FeeAccount account = feeAccountService.create(form(enrollment.getId(), "24000", "4000"), admin);

        PaymentForm payment = new PaymentForm();
        payment.setFeeAccountId(account.getId());
        payment.setAmount(new BigDecimal("5000"));
        payment.setPaymentDate(LocalDate.of(2026, 9, 5));
        payment.setPaymentMethod(Payment.Method.CASH);
        paymentService.record(payment, admin);

        // Amount changes are locked.
        FeeAccountForm changed = form(account.getId(), "30000", "4000");
        assertThatThrownBy(() -> feeAccountService.update(account.getId(), changed))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("can no longer be changed");

        // Due date and notes remain editable even with payments recorded.
        FeeAccountForm touchUp = form(account.getId(), "24000", "4000");
        touchUp.setDueDate(LocalDate.of(2026, 10, 31));
        touchUp.setNotes("Second installment due end of October.");
        feeAccountService.update(account.getId(), touchUp);

        FeeAccount updated = feeAccountService.getWithDetails(account.getId());
        assertThat(updated.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(updated.getNotes()).contains("Second installment");
        assertThat(feeAccountService.outstandingOf(updated)).isEqualByComparingTo(new BigDecimal("15000.00"));
    }

    @Test
    void discountBeyondGrossIsAlsoRejectedOnEdit() {
        buildFixture();
        Enrollment enrollment = enrolled(fixtures.student("Edit Discount Student"), morningBatch);
        FeeAccount account = feeAccountService.create(form(enrollment.getId(), "24000", null), admin);

        assertThatThrownBy(() -> feeAccountService.update(account.getId(),
                form(account.getId(), "24000", "30000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Discount cannot exceed the gross fee");
    }

    @Test
    void sameCourseTransferRepointsTheAgreementInsteadOfCreatingASecondOne() {
        buildFixture();
        Enrollment enrollment = enrolled(fixtures.student("Transfer Fee Student"), morningBatch);
        FeeAccount account = feeAccountService.create(form(enrollment.getId(), "24000", "4000"), admin);

        PaymentForm payment = new PaymentForm();
        payment.setFeeAccountId(account.getId());
        payment.setAmount(new BigDecimal("8000"));
        payment.setPaymentDate(LocalDate.of(2026, 9, 5));
        payment.setPaymentMethod(Payment.Method.UPI);
        paymentService.record(payment, admin);

        TransferForm transfer = new TransferForm();
        transfer.setStudentId(account.getStudent().getId());
        transfer.setTargetBatchId(eveningBatch.getId());
        transfer.setTransferDate(LocalDate.of(2026, 9, 10));
        Enrollment newEnrollment = enrollmentService.transfer(transfer);

        // Exactly one agreement exists for this (student, course, year).
        assertThat(feeAccountRepository.count()).isEqualTo(1);
        assertThat(feeAccountRepository
                .existsByStudentIdAndCourseIdAndAcademicYear(account.getStudent().getId(), course.getId(), "2026-27"))
                .isTrue();

        FeeAccount reloaded = feeAccountService.getWithDetails(account.getId());
        assertThat(reloaded.getEnrollment().getId()).isEqualTo(newEnrollment.getId());
        // Payment history is untouched; the balance carries over.
        assertThat(feeAccountService.outstandingOf(reloaded)).isEqualByComparingTo(new BigDecimal("12000.00"));

        // The (student, course, year) rule is enforced in the service, so
        // creating an agreement for the new enrollment is rejected too:
        assertThatThrownBy(() -> feeAccountService.create(form(newEnrollment.getId(), "24000", null), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has a fee agreement for");
    }
}
