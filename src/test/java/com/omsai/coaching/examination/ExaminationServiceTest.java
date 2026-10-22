package com.omsai.coaching.examination;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentService;
import com.omsai.coaching.enrollment.TransferForm;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.support.Fixtures;
import com.omsai.coaching.teacher.Teacher;
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
 * Examination and result rules: who may create an examination, which
 * students are on the roster, what a valid mark is, who publishes the
 * results and what happens to the history when a student transfers.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExaminationServiceTest {

    @Autowired
    private ExaminationService examinationService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private Fixtures fixtures;

    private UserAccount admin;
    private Course course;
    private Batch batch;
    private BatchSubject assignment;
    private LocalDate examDate;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("exam.admin");
        course = fixtures.course("Examination Course");
        batch = fixtures.batch(course, "Morning");
        assignment = fixtures.assignCourseSubject(batch,
                fixtures.assign(course, fixtures.subject("Examination Subject")));
        examDate = LocalDate.of(2026, 9, 20);
    }

    private ExaminationForm form(String title, String maximumMarks) {
        ExaminationForm form = new ExaminationForm();
        form.setBatchSubjectId(assignment.getId());
        form.setTitle(title);
        form.setExaminationDate(examDate);
        form.setMaximumMarks(new BigDecimal(maximumMarks));
        return form;
    }

    private List<ExaminationService.ResultInput> result(Enrollment enrollment, String status, String marks) {
        return List.of(new ExaminationService.ResultInput(enrollment.getId(), status, marks, null));
    }

    // ----- creating an examination -----------------------------------------

    @Test
    void anAdministratorCreatesAnExaminationAsADraft() {
        Examination examination = examinationService.create(form("Unit test 1", "100"), admin);

        assertThat(examination.getId()).isNotNull();
        assertThat(examination.getStatus()).isEqualTo(Examination.Status.DRAFT);
        assertThat(examination.getMaximumMarks()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(examination.getBatchSubject().getId()).isEqualTo(assignment.getId());
    }

    @Test
    void theMaximumMarksAreValidated() {
        assertThatThrownBy(() -> examinationService.create(form("Zero", "0"), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> examinationService.create(form("Negative", "-5"), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> examinationService.create(form("Too precise", "10.005"), admin))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aTeacherMayOnlyManageTheExaminationsOfTheirOwnClasses() {
        UserAccount teacherUser = fixtures.teacherUser("exam.teacher");
        Teacher teacher = fixtures.teacherLinkedTo(teacherUser, "Exam Teacher");
        fixtures.assignTeacher(assignment, teacher);

        // Their own class: allowed.
        Examination own = examinationService.create(form("Own exam", "50"), teacherUser);
        assertThat(own.getId()).isNotNull();

        // Another teacher's class: refused.
        BatchSubject foreign = fixtures.assignCourseSubject(batch,
                fixtures.assign(course, fixtures.subject("Foreign Subject")));
        fixtures.assignTeacher(foreign, fixtures.teacher("Foreign Teacher"));
        ExaminationForm foreignForm = form("Foreign exam", "50");
        foreignForm.setBatchSubjectId(foreign.getId());
        assertThatThrownBy(() -> examinationService.create(foreignForm, teacherUser))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // ----- the roster and the marks ----------------------------------------

    @Test
    void onlyTheStudentsOfTheRosterAreEligible() {
        Enrollment onRoster = fixtures.activeEnrollment(fixtures.student("Exam On Roster"), batch,
                examDate.minusDays(30));
        Enrollment later = fixtures.activeEnrollment(fixtures.student("Exam Late Joiner"), batch,
                examDate.plusDays(5));
        Examination examination = examinationService.create(form("Unit test 1", "100"), admin);

        assertThat(examinationService.rosterFor(examination)).hasSize(1);
        assertThat(examinationService.rosterFor(examination).get(0).getId()).isEqualTo(onRoster.getId());

        // A student who enrolled after the examination cannot have a result.
        assertThatThrownBy(() -> examinationService.recordResults(
                examination.getId(), result(later, "PRESENT", "50"), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("enrolled only on");
    }

    @Test
    void theMarksAreValidated() {
        Enrollment student = fixtures.activeEnrollment(fixtures.student("Exam Marks Student"), batch,
                examDate.minusDays(30));
        Examination examination = examinationService.create(form("Unit test 1", "100"), admin);

        assertThatThrownBy(() -> examinationService.recordResults(
                examination.getId(), result(student, "PRESENT", "150"), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("above the maximum");
        assertThatThrownBy(() -> examinationService.recordResults(
                examination.getId(), result(student, "PRESENT", "-5"), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be negative");
        assertThatThrownBy(() -> examinationService.recordResults(
                examination.getId(), result(student, "PRESENT", ""), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("needs a mark");
        // An absent student has no mark - that is not a mark of zero.
        assertThatThrownBy(() -> examinationService.recordResults(
                examination.getId(), result(student, "ABSENT", "50"), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must not have a mark");
    }

    @Test
    void anAbsentStudentHasNoMarkAtAll() {
        Enrollment student = fixtures.activeEnrollment(fixtures.student("Exam Absent Student"), batch,
                examDate.minusDays(30));
        Examination examination = examinationService.create(form("Unit test 1", "100"), admin);

        examinationService.recordResults(examination.getId(), result(student, "ABSENT", null), admin);

        List<ExaminationResult> results = examinationService.resultsFor(examination);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo(ExaminationResult.Status.ABSENT);
        assertThat(results.get(0).getMarks()).isNull();
        assertThat(results.get(0).percentage(examination.getMaximumMarks())).isNull();
    }

    @Test
    void aResultIsStoredOnlyOnce() {
        Enrollment student = fixtures.activeEnrollment(fixtures.student("Exam Once Student"), batch,
                examDate.minusDays(30));
        Examination examination = examinationService.create(form("Unit test 1", "100"), admin);

        examinationService.recordResults(examination.getId(), result(student, "PRESENT", "50"), admin);
        examinationService.recordResults(examination.getId(), result(student, "PRESENT", "70"), admin);

        List<ExaminationResult> results = examinationService.resultsFor(examination);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMarks()).isEqualByComparingTo(new BigDecimal("70"));
    }

    // ----- publication ------------------------------------------------------

    @Test
    void aDraftResultIsNotVisibleToTheStudent() {
        Enrollment student = fixtures.activeEnrollment(fixtures.student("Exam Draft Student"), batch,
                examDate.minusDays(30));
        Examination examination = examinationService.create(form("Unit test 1", "100"), admin);
        examinationService.recordResults(examination.getId(), result(student, "PRESENT", "80"), admin);

        assertThat(examinationService.publishedResultsFor(student.getId())).isEmpty();

        examinationService.publish(examination.getId(), admin);

        List<ExaminationResult> published = examinationService.publishedResultsFor(student.getId());
        assertThat(published).hasSize(1);
        assertThat(published.get(0).getMarks()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(published.get(0).isPublished()).isTrue();
    }

    @Test
    void onlyAnAdministratorPublishes() {
        UserAccount teacherUser = fixtures.teacherUser("exam.publisher");
        Teacher teacher = fixtures.teacherLinkedTo(teacherUser, "Exam Publisher");
        fixtures.assignTeacher(assignment, teacher);
        Examination examination = examinationService.create(form("Unit test 1", "100"), teacherUser);

        assertThatThrownBy(() -> examinationService.publish(examination.getId(), teacherUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only an administrator can publish");
    }

    @Test
    void aCancelledExaminationKeepsItsResults() {
        Enrollment student = fixtures.activeEnrollment(fixtures.student("Exam Cancelled Student"), batch,
                examDate.minusDays(30));
        Examination examination = examinationService.create(form("Unit test 1", "100"), admin);
        examinationService.recordResults(examination.getId(), result(student, "PRESENT", "60"), admin);

        examinationService.cancel(examination.getId(), admin);

        assertThat(examinationService.get(examination.getId()).getStatus())
                .isEqualTo(Examination.Status.CANCELLED);
        assertThat(examinationService.resultsFor(examination)).hasSize(1);
    }

    @Test
    void aResultSurvivesATransferToAnotherBatch() {
        Batch evening = fixtures.batch(course, "Evening");
        Enrollment enrollment = fixtures.activeEnrollment(fixtures.student("Exam Transfer Student"),
                batch, examDate.minusDays(60));
        Examination examination = examinationService.create(form("Unit test 1", "100"), admin);
        examinationService.recordResults(examination.getId(), result(enrollment, "PRESENT", "75"), admin);

        TransferForm transfer = new TransferForm();
        transfer.setStudentId(enrollment.getStudent().getId());
        transfer.setTargetBatchId(evening.getId());
        transfer.setTransferDate(examDate.plusDays(10));
        enrollmentService.transfer(transfer);

        // The result still belongs to the enrollment the student sat in.
        List<ExaminationResult> results = examinationService.resultsForEnrollment(enrollment.getId());
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMarks()).isEqualByComparingTo(new BigDecimal("75"));
    }

    @Test
    void theSummaryCountsPresentAbsentAndExcusedSeparately() {
        Enrollment present = fixtures.activeEnrollment(fixtures.student("Summary Present"), batch,
                examDate.minusDays(30));
        Enrollment absent = fixtures.activeEnrollment(fixtures.student("Summary Absent"), batch,
                examDate.minusDays(30));
        Enrollment excused = fixtures.activeEnrollment(fixtures.student("Summary Excused"), batch,
                examDate.minusDays(30));
        Examination examination = examinationService.create(form("Unit test 1", "100"), admin);
        examinationService.recordResults(examination.getId(),
                List.of(new ExaminationService.ResultInput(present.getId(), "PRESENT", "80", null),
                        new ExaminationService.ResultInput(absent.getId(), "ABSENT", null, null),
                        new ExaminationService.ResultInput(excused.getId(), "EXCUSED", null, null)), admin);

        List<ExaminationService.ExamSummary> summaries =
                examinationService.summariesFor(List.of(examination));
        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).presentCount()).isEqualTo(1);
        assertThat(summaries.get(0).absentCount()).isEqualTo(1);
        assertThat(summaries.get(0).excusedCount()).isEqualTo(1);
        // The average only counts the students who sat the examination.
        assertThat(summaries.get(0).averageMarks()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(summaries.get(0).highestMarks()).isEqualByComparingTo(new BigDecimal("80"));
    }
}
