package com.omsai.coaching.attendance;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.CourseSubject;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.support.Fixtures;
import com.omsai.coaching.teacher.Teacher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Class-session business rules: single classes tied to existing
 * assignments, teacher ownership enforced in the service layer, and
 * the OPEN/COMPLETED/CANCELLED lifecycle that never deletes history.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClassSessionServiceTest {

    @Autowired
    private ClassSessionService sessionService;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private Fixtures fixtures;

    private Course course;
    private Batch batch;
    private BatchSubject assignment;

    private void buildFixture() {
        course = fixtures.course("Session Course");
        batch = fixtures.batch(course, "Morning");
        CourseSubject courseSubject = fixtures.assign(course, fixtures.subject("Session Subject"));
        assignment = fixtures.assignCourseSubject(batch, courseSubject);
    }

    private SessionForm form(Long batchSubjectId, LocalDate date) {
        SessionForm form = new SessionForm();
        form.setBatchSubjectId(batchSubjectId);
        form.setSessionDate(date);
        form.setStartTime(LocalTime.of(10, 0));
        form.setEndTime(LocalTime.of(11, 0));
        form.setTopic("Quadratic equations");
        return form;
    }

    @Test
    void adminCreatesSessionForActiveAssignment() {
        buildFixture();
        UserAccount admin = fixtures.adminUser("creator");

        ClassSession created = sessionService.create(form(assignment.getId(), LocalDate.of(2026, 9, 5)), admin);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(ClassSession.Status.OPEN);
        assertThat(created.getBatchSubject().getId()).isEqualTo(assignment.getId());
        assertThat(created.getCreatedBy().getId()).isEqualTo(admin.getId());
    }

    @Test
    void teacherCreatesSessionForOwnAssignment() {
        buildFixture();
        UserAccount teacherUser = fixtures.teacherUser("own.teacher");
        Teacher teacher = fixtures.teacherLinkedTo(teacherUser, "Own Teacher");
        fixtures.assignTeacher(assignment, teacher);

        ClassSession created = sessionService.create(form(assignment.getId(), LocalDate.of(2026, 9, 5)), teacherUser);

        assertThat(created.getId()).isNotNull();
    }

    @Test
    void teacherCannotCreateSessionForAnotherTeachersAssignment() {
        buildFixture();
        UserAccount stranger = fixtures.teacherUser("stranger.teacher");
        fixtures.teacherLinkedTo(stranger, "Stranger Teacher");

        assertThatThrownBy(() -> sessionService.create(form(assignment.getId(), LocalDate.of(2026, 9, 5)), stranger))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void teacherWithoutLinkedProfileIsDenied() {
        buildFixture();
        UserAccount orphan = fixtures.teacherUser("orphan.teacher");

        assertThatThrownBy(() -> sessionService.create(form(assignment.getId(), LocalDate.of(2026, 9, 5)), orphan))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sessionCannotBeCreatedForInactiveBatch() {
        buildFixture();
        fixtures.deactivate(batch);
        UserAccount admin = fixtures.adminUser("creator");

        assertThatThrownBy(() -> sessionService.create(form(assignment.getId(), LocalDate.of(2026, 9, 5)), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void endTimeMustBeAfterStartTime() {
        buildFixture();
        UserAccount admin = fixtures.adminUser("creator");
        SessionForm bad = form(assignment.getId(), LocalDate.of(2026, 9, 5));
        bad.setEndTime(LocalTime.of(9, 0));

        assertThatThrownBy(() -> sessionService.create(bad, admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("End time must be after the start time");
    }

    @Test
    void onlyOpenSessionsCanBeEdited() {
        buildFixture();
        UserAccount admin = fixtures.adminUser("creator");
        ClassSession created = sessionService.create(form(assignment.getId(), LocalDate.of(2026, 9, 5)), admin);
        sessionService.complete(created.getId(), admin);

        SessionForm changed = form(assignment.getId(), LocalDate.of(2026, 9, 6));
        assertThatThrownBy(() -> sessionService.update(created.getId(), changed, admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only OPEN sessions can be edited");
    }

    @Test
    void completingAndCancellingPreserveTheRecord() {
        buildFixture();
        UserAccount admin = fixtures.adminUser("creator");
        ClassSession first = sessionService.create(form(assignment.getId(), LocalDate.of(2026, 9, 5)), admin);
        ClassSession second = sessionService.create(form(assignment.getId(), LocalDate.of(2026, 9, 6)), admin);

        sessionService.complete(first.getId(), admin);
        sessionService.cancel(second.getId(), admin);

        assertThat(sessionService.getAuthorized(first.getId(), admin).getStatus())
                .isEqualTo(ClassSession.Status.COMPLETED);
        assertThat(sessionService.getAuthorized(second.getId(), admin).getStatus())
                .isEqualTo(ClassSession.Status.CANCELLED);
        assertThat(attendanceRepository.count()).isZero();
    }

    @Test
    void sessionWithAttendanceCannotBeCancelled() {
        buildFixture();
        UserAccount admin = fixtures.adminUser("creator");
        ClassSession created = sessionService.create(form(assignment.getId(), LocalDate.of(2026, 9, 5)), admin);

        Enrollment enrolled = fixtures.activeEnrollment(fixtures.student("Marked Student"), batch,
                LocalDate.of(2026, 9, 1));
        AttendanceTestSupport.mark(attendanceRepository, created, enrolled, Attendance.Status.PRESENT, admin);

        assertThatThrownBy(() -> sessionService.cancel(created.getId(), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has attendance records");
    }

    @Test
    void teacherCanOnlyViewOwnSessions() {
        buildFixture();
        UserAccount owner = fixtures.teacherUser("owner.teacher");
        Teacher ownerTeacher = fixtures.teacherLinkedTo(owner, "Owner Teacher");
        fixtures.assignTeacher(assignment, ownerTeacher);
        UserAccount admin = fixtures.adminUser("creator");
        ClassSession created = sessionService.create(form(assignment.getId(), LocalDate.of(2026, 9, 5)), admin);

        assertThat(sessionService.getAuthorized(created.getId(), owner).getId()).isEqualTo(created.getId());

        // A second teacher must not even view the session.
        UserAccount stranger = fixtures.teacherUser("stranger2.teacher");
        fixtures.teacherLinkedTo(stranger, "Stranger Two");
        assertThatThrownBy(() -> sessionService.getAuthorized(created.getId(), stranger))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rosterFollowsTheEnrollmentPeriodNotTheCurrentStatus() {
        buildFixture();
        UserAccount admin = fixtures.adminUser("creator");
        LocalDate classDate = LocalDate.of(2026, 9, 20);
        ClassSession created = sessionService.create(form(assignment.getId(), classDate), admin);

        // Enrolled before the class date: on the roster.
        Enrollment early = fixtures.activeEnrollment(fixtures.student("Early Student"), batch,
                LocalDate.of(2026, 9, 1));
        // Enrolled after the class date: not on the roster.
        fixtures.activeEnrollment(fixtures.student("Late Student"), batch, LocalDate.of(2026, 9, 25));
        // Enrolled before but withdrawn (ended) before the class date: not on the roster.
        Enrollment gone = fixtures.activeEnrollment(fixtures.student("Gone Student"), batch,
                LocalDate.of(2026, 9, 1));
        gone.setStatus(Enrollment.Status.WITHDRAWN);
        gone.setEndDate(LocalDate.of(2026, 9, 10));

        List<AttendanceMarkRow> roster = sessionService.rosterFor(created);

        List<Long> ids = new ArrayList<>(roster.stream().map(AttendanceMarkRow::enrollmentId).toList());
        assertThat(ids).containsExactly(early.getId());
    }
}
