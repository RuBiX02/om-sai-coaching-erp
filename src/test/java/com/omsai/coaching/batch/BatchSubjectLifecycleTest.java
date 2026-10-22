package com.omsai.coaching.batch;

import com.omsai.coaching.attendance.ClassSession;
import com.omsai.coaching.attendance.ClassSessionRepository;
import com.omsai.coaching.attendance.ClassSessionService;
import com.omsai.coaching.attendance.SessionForm;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.examination.ExaminationForm;
import com.omsai.coaching.examination.ExaminationService;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.support.Fixtures;
import com.omsai.coaching.timetable.TimetableForm;
import com.omsai.coaching.timetable.TimetableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The lifecycle of a batch-subject assignment - the entity that makes
 * a "class".
 *
 * <p>An assignment that has never been used is disposable and may be
 * deleted. An assignment that has been taught carries class sessions,
 * attendance, timetable entries and examinations; that history must
 * survive, so the assignment is deactivated instead of deleted, and the
 * user is told why. Deactivating hides the assignment from the pickers
 * for new classes but changes nothing about the past.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BatchSubjectLifecycleTest {

    @Autowired
    private BatchService batchService;

    @Autowired
    private BatchSubjectRepository batchSubjectRepository;

    @Autowired
    private ClassSessionService sessionService;

    @Autowired
    private ClassSessionRepository sessionRepository;

    @Autowired
    private TimetableService timetableService;

    @Autowired
    private ExaminationService examinationService;

    @Autowired
    private Fixtures fixtures;

    private UserAccount admin;
    private Course course;
    private Batch batch;
    private BatchSubject assignment;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("lifecycle.admin");
        course = fixtures.course("Lifecycle Course");
        batch = fixtures.batch(course, "Morning");
        assignment = fixtures.assignCourseSubject(batch,
                fixtures.assign(course, fixtures.subject("Lifecycle Subject")));
    }

    // ----- removal ---------------------------------------------------------

    @Test
    void anAssignmentThatWasNeverUsedCanBeRemoved() {
        batchService.removeSubject(assignment.getId());

        assertThat(batchSubjectRepository.findById(assignment.getId())).isEmpty();
    }

    @Test
    void anAssignmentWithClassSessionsCannotBeRemoved() {
        createSession();

        assertThatThrownBy(() -> batchService.removeSubject(assignment.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be removed from the batch")
                .hasMessageContaining("class session")
                .hasMessageContaining("Deactivate the subject instead");
    }

    @Test
    void anAssignmentWithATimetableEntryCannotBeRemoved() {
        createTimetableEntry();

        assertThatThrownBy(() -> batchService.removeSubject(assignment.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("timetable entry");
    }

    @Test
    void anAssignmentWithAnExaminationCannotBeRemoved() {
        createExamination();

        assertThatThrownBy(() -> batchService.removeSubject(assignment.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("examination");
    }

    @Test
    void aRefusedRemovalLeavesTheAssignmentAndItsClassesUntouched() {
        createSession();

        assertThatThrownBy(() -> batchService.removeSubject(assignment.getId()))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(batchSubjectRepository.findById(assignment.getId())).isPresent();
        assertThat(sessionRepository.countByBatchSubjectId(assignment.getId())).isEqualTo(1);
    }

    @Test
    void usageCountsEveryKindOfDependency() {
        createSession();
        createTimetableEntry();
        createExamination();

        BatchService.SubjectUsage usage = batchService.usageOf(assignment.getId());

        assertThat(usage.classSessions()).isEqualTo(1);
        assertThat(usage.timetableEntries()).isEqualTo(1);
        assertThat(usage.examinations()).isEqualTo(1);
        assertThat(usage.isRemovable()).isFalse();
        assertThat(usage.describe())
                .contains("1 class session")
                .contains("1 timetable entry")
                .contains("1 examination");
    }

    @Test
    void anUnusedAssignmentReportsNoDependencies() {
        BatchService.SubjectUsage usage = batchService.usageOf(assignment.getId());

        assertThat(usage.isRemovable()).isTrue();
    }

    // ----- deactivation ----------------------------------------------------

    @Test
    void deactivatingHidesTheAssignmentFromThePickers() {
        batchService.setSubjectActive(assignment.getId(), false);

        assertThat(batchSubjectRepository.findForActiveBatches())
                .noneMatch(bs -> bs.getId().equals(assignment.getId()));
        assertThat(batchSubjectRepository.findAllWithContext())
                .noneMatch(bs -> bs.getId().equals(assignment.getId()));
    }

    @Test
    void deactivatingKeepsTheAssignmentVisibleOnTheBatchPage() {
        batchService.setSubjectActive(assignment.getId(), false);

        List<BatchSubject> onBatch = batchSubjectRepository.findForBatch(batch.getId());

        assertThat(onBatch).extracting(BatchSubject::getId).contains(assignment.getId());
        assertThat(onBatch.get(0).isActive()).isFalse();
    }

    @Test
    void deactivatingKeepsEveryExistingClass() {
        createSession();

        batchService.setSubjectActive(assignment.getId(), false);

        assertThat(sessionRepository.countByBatchSubjectId(assignment.getId())).isEqualTo(1);
        ClassSession session = sessionRepository
                .findByBatchSubjectIdAndSessionDate(assignment.getId(), LocalDate.of(2026, 9, 7))
                .orElseThrow();
        assertThat(session.getStatus()).isEqualTo(ClassSession.Status.OPEN);
    }

    @Test
    void reactivatingPutsTheAssignmentBackIntoThePickers() {
        batchService.setSubjectActive(assignment.getId(), false);

        batchService.setSubjectActive(assignment.getId(), true);

        assertThat(batchSubjectRepository.findForActiveBatches())
                .anyMatch(bs -> bs.getId().equals(assignment.getId()));
        assertThat(batchSubjectRepository.findById(assignment.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void deactivatingTwiceIsRefusedWithAnExplanation() {
        batchService.setSubjectActive(assignment.getId(), false);

        assertThatThrownBy(() -> batchService.setSubjectActive(assignment.getId(), false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already deactivated");
    }

    @Test
    void activatingAnActiveAssignmentIsRefused() {
        assertThatThrownBy(() -> batchService.setSubjectActive(assignment.getId(), true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already being taught");
    }

    @Test
    void aSubjectOfAnInactiveBatchCannotBeReactivated() {
        batchService.setSubjectActive(assignment.getId(), false);
        fixtures.deactivate(batch);

        assertThatThrownBy(() -> batchService.setSubjectActive(assignment.getId(), true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void addingADeactivatedSubjectReactivatesItInsteadOfDuplicatingIt() {
        batchService.setSubjectActive(assignment.getId(), false);

        batchService.addSubject(batch.getId(), assignment.getCourseSubject().getId());

        assertThat(batchSubjectRepository.findForBatch(batch.getId()))
                .filteredOn(bs -> bs.getCourseSubject().getId().equals(assignment.getCourseSubject().getId()))
                .hasSize(1)
                .allMatch(BatchSubject::isActive);
    }

    @Test
    void aDeactivatedSubjectCanBeAddedAgainFromThePicker() {
        batchService.setSubjectActive(assignment.getId(), false);

        List<com.omsai.coaching.batch.CourseSubject> assignable =
                batchService.assignableCourseSubjects(batch);

        assertThat(assignable)
                .extracting(com.omsai.coaching.batch.CourseSubject::getId)
                .contains(assignment.getCourseSubject().getId());
    }

    // ----- helpers ---------------------------------------------------------

    private void createSession() {
        SessionForm form = new SessionForm();
        form.setBatchSubjectId(assignment.getId());
        form.setSessionDate(LocalDate.of(2026, 9, 7));
        form.setStartTime(LocalTime.of(10, 0));
        form.setEndTime(LocalTime.of(11, 0));
        sessionService.create(form, admin);
    }

    private void createTimetableEntry() {
        TimetableForm form = new TimetableForm();
        form.setBatchSubjectId(assignment.getId());
        form.setDayOfWeek(DayOfWeek.MONDAY);
        form.setStartTime(LocalTime.of(9, 0));
        form.setEndTime(LocalTime.of(10, 0));
        form.setClassroom("Room 1");
        form.setEffectiveFrom(LocalDate.of(2026, 9, 1));
        timetableService.create(form, admin);
    }

    private void createExamination() {
        ExaminationForm form = new ExaminationForm();
        form.setBatchSubjectId(assignment.getId());
        form.setTitle("Unit test");
        form.setExaminationDate(LocalDate.of(2026, 9, 20));
        form.setMaximumMarks(new BigDecimal("100"));
        examinationService.create(form, admin);
    }
}
