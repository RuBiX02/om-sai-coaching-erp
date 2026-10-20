package com.omsai.coaching.enrollment;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Enrollment business rules: eligibility checks, duplicate prevention and
 * transfers that preserve complete history.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnrollmentServiceTest {

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private Fixtures fixtures;

    private Course course;
    private Batch batchOne;
    private Batch batchTwo;
    private com.omsai.coaching.course.Course otherCourse;
    private Batch otherCourseBatch;

    private void buildFixture() {
        course = fixtures.course("Physics Course");
        batchOne = fixtures.batch(course, "Morning");
        batchTwo = fixtures.batch(course, "Evening");
        otherCourse = fixtures.course("Chemistry Course");
        otherCourseBatch = fixtures.batch(otherCourse, "Morning");
    }

    private EnrollForm enrollForm(Long studentId, Long batchId) {
        EnrollForm form = new EnrollForm();
        form.setStudentId(studentId);
        form.setBatchId(batchId);
        form.setEnrollmentDate(LocalDate.of(2026, 9, 2));
        return form;
    }

    private TransferForm transferForm(Long studentId, Long targetBatchId) {
        TransferForm form = new TransferForm();
        form.setStudentId(studentId);
        form.setTargetBatchId(targetBatchId);
        form.setTransferDate(LocalDate.of(2026, 9, 10));
        return form;
    }

    @Test
    void enroll_persistsActiveEnrollment() {
        buildFixture();
        var student = fixtures.student("Enrolled Student");
        Enrollment enrollment = enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId()));

        assertThat(enrollment.getId()).isNotNull();
        assertThat(enrollment.getStatus()).isEqualTo(Enrollment.Status.ACTIVE);
        assertThat(enrollment.getBatch().getId()).isEqualTo(batchOne.getId());
        assertThat(enrollment.getCourse().getId()).isEqualTo(course.getId());
    }

    @Test
    void enroll_rejectsSecondActiveEnrollmentInSameBatch() {
        buildFixture();
        var student = fixtures.student("Dup Batch Student");
        enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId()));

        assertThatThrownBy(() -> enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This student already has an active enrollment in that batch.");
    }

    @Test
    void enroll_rejectsSecondActiveEnrollmentInSameCourse() {
        buildFixture();
        var student = fixtures.student("Dup Course Student");
        enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId()));

        assertThatThrownBy(() -> enrollmentService.enroll(enrollForm(student.getId(), batchTwo.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("This student already has an active enrollment in another batch of \"")
                .hasMessageContaining("Use a transfer to change batches.");
    }

    @Test
    void enroll_allowsEnrollmentInAnotherCourse() {
        buildFixture();
        var student = fixtures.student("Two Course Student");
        enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId()));

        Enrollment second = enrollmentService.enroll(enrollForm(student.getId(), otherCourseBatch.getId()));
        assertThat(second.getCourse().getId()).isEqualTo(otherCourse.getId());
    }

    @Test
    void enroll_rejectsInactiveStudent() {
        buildFixture();
        var student = fixtures.student("Inactive Student");
        fixtures.deactivate(student);

        assertThatThrownBy(() -> enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(student.getFullName() + " is inactive and cannot be enrolled.");
    }

    @Test
    void enroll_rejectsInactiveBatch() {
        buildFixture();
        var student = fixtures.student("Inactive Batch Student");
        fixtures.deactivate(batchOne);

        assertThatThrownBy(() -> enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is inactive.");
    }

    @Test
    void enroll_rejectsInactiveCourseOfBatch() {
        buildFixture();
        var student = fixtures.student("Inactive Course Student");
        fixtures.deactivate(course);

        assertThatThrownBy(() -> enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is inactive, so its batches cannot accept enrollments.");
    }

    @Test
    void enroll_rejectsUnknownStudentOrBatch() {
        buildFixture();
        var student = fixtures.student("Unknown Target");

        assertThatThrownBy(() -> enrollmentService.enroll(enrollForm(student.getId(), 999_998L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Batch not found.");

        assertThatThrownBy(() -> enrollmentService.enroll(enrollForm(999_999L, batchOne.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Student not found.");
    }

    @Test
    void transfer_closesPreviousRecordAndCreatesNewActive() {
        buildFixture();
        var student = fixtures.student("Transferred Student");
        Enrollment original = enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId()));

        enrollmentService.transfer(transferForm(student.getId(), batchTwo.getId()));

        List<Enrollment> history = enrollmentRepository.findForStudent(student.getId());
        assertThat(history).hasSize(2);

        Enrollment closed = history.stream()
                .filter(e -> e.getId().equals(original.getId()))
                .findFirst().orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(Enrollment.Status.COMPLETED);
        assertThat(closed.getBatch().getId()).isEqualTo(batchOne.getId());

        Enrollment moved = history.stream()
                .filter(e -> !e.getId().equals(original.getId()))
                .findFirst().orElseThrow();
        assertThat(moved.getStatus()).isEqualTo(Enrollment.Status.ACTIVE);
        assertThat(moved.getBatch().getId()).isEqualTo(batchTwo.getId());
        assertThat(moved.getCourse().getId()).isEqualTo(course.getId());
    }

    @Test
    void transfer_rejectsTargetBatchOfAnotherCourse() {
        buildFixture();
        var student = fixtures.student("Cross Course Student");
        enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId()));

        assertThatThrownBy(() -> enrollmentService.transfer(transferForm(student.getId(), otherCourseBatch.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("This student has no active enrollment in a batch of \"")
                .hasMessageContaining("Use a new enrollment instead of a transfer.");
    }

    @Test
    void transfer_rejectsTransferToSameBatch() {
        buildFixture();
        var student = fixtures.student("Same Batch Student");
        enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId()));

        assertThatThrownBy(() -> enrollmentService.transfer(transferForm(student.getId(), batchOne.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The student is already active in that batch.");
    }

    @Test
    void transfer_requiresExistingActiveEnrollment() {
        buildFixture();
        var student = fixtures.student("No Enroll Student");

        assertThatThrownBy(() -> enrollmentService.transfer(transferForm(student.getId(), batchTwo.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no active enrollment in a batch of");
    }

    @Test
    void transfer_rejectsInactiveStudent() {
        buildFixture();
        var student = fixtures.student("Inactive Transfer Student");
        enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId()));
        fixtures.deactivate(student);

        assertThatThrownBy(() -> enrollmentService.transfer(transferForm(student.getId(), batchTwo.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(student.getFullName() + " is inactive and cannot be transferred.");
    }

    @Test
    void transfer_rejectsInactiveTargetBatch() {
        buildFixture();
        var student = fixtures.student("Inactive Target Student");
        enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId()));
        fixtures.deactivate(batchTwo);

        assertThatThrownBy(() -> enrollmentService.transfer(transferForm(student.getId(), batchTwo.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Target batch \"")
                .hasMessageContaining("is inactive.");
    }

    @Test
    void withdraw_marksEnrollmentAndFreesBatch() {
        buildFixture();
        var student = fixtures.student("Withdrawn Student");
        Enrollment enrollment = enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId()));
        enrollmentService.withdraw(enrollment.getId());

        Enrollment reloaded = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Enrollment.Status.WITHDRAWN);

        // The student can be enrolled again because no ACTIVE record remains.
        Enrollment again = enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId()));
        assertThat(again.getStatus()).isEqualTo(Enrollment.Status.ACTIVE);
        assertThat(again.getId()).isNotEqualTo(enrollment.getId());
    }

    @Test
    void complete_thenRejectFurtherUpdates() {
        buildFixture();
        var student = fixtures.student("Completed Student");
        Enrollment enrollment = enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId()));
        enrollmentService.complete(enrollment.getId());

        assertThatThrownBy(() -> enrollmentService.complete(enrollment.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only ACTIVE enrollments can be updated (this one is COMPLETED)");

        assertThatThrownBy(() -> enrollmentService.withdraw(enrollment.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only ACTIVE enrollments can be updated");
    }

    @Test
    void afterWithdraw_transferRequiresNewEnrollment() {
        buildFixture();
        var student = fixtures.student("Withdraw Transfer Student");
        Enrollment enrollment = enrollmentService.enroll(enrollForm(student.getId(), batchOne.getId()));
        enrollmentService.withdraw(enrollment.getId());

        assertThatThrownBy(() -> enrollmentService.transfer(transferForm(student.getId(), batchTwo.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no active enrollment in a batch of");
    }
}
