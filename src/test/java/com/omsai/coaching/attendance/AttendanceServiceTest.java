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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Attendance rules: one row per session+enrollment, eligibility from
 * the enrollment's effective period, corrections that update in place
 * (never delete), teacher lockout after completion, and the
 * documented percentage formula.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceServiceTest {

    @Autowired
    private AttendanceService attendanceService;

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
        course = fixtures.course("Attendance Course");
        batch = fixtures.batch(course, "Morning");
        CourseSubject courseSubject = fixtures.assign(course, fixtures.subject("Attendance Subject"));
        assignment = fixtures.assignCourseSubject(batch, courseSubject);
    }

    private ClassSession openSession(LocalDate date, UserAccount creator) {
        SessionForm form = new SessionForm();
        form.setBatchSubjectId(assignment.getId());
        form.setSessionDate(date);
        form.setStartTime(LocalTime.of(9, 0));
        form.setEndTime(LocalTime.of(10, 0));
        return sessionService.create(form, creator);
    }

    private AttendanceForm.Row row(Long enrollmentId, Attendance.Status status, String remarks) {
        AttendanceForm.Row row = new AttendanceForm.Row();
        row.setEnrollmentId(enrollmentId);
        row.setStatus(status);
        row.setRemarks(remarks);
        return row;
    }

    @Test
    void recordsFullRosterInOneSubmission() {
        buildFixture();
        UserAccount admin = fixtures.adminUser("marker");
        LocalDate date = LocalDate.of(2026, 9, 10);
        ClassSession session = openSession(date, admin);
        Enrollment one = fixtures.activeEnrollment(fixtures.student("Student One"), batch, LocalDate.of(2026, 9, 1));
        Enrollment two = fixtures.activeEnrollment(fixtures.student("Student Two"), batch, LocalDate.of(2026, 9, 1));

        AttendanceForm form = new AttendanceForm();
        form.setRows(List.of(row(one.getId(), Attendance.Status.PRESENT, ""),
                row(two.getId(), Attendance.Status.ABSENT, "Sick")));

        int saved = attendanceService.record(session.getId(), form, admin);

        assertThat(saved).isEqualTo(2);
        List<Attendance> stored = attendanceRepository.findForSession(session.getId());
        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(Attendance::getStatus)
                .containsExactlyInAnyOrder(Attendance.Status.PRESENT, Attendance.Status.ABSENT);
    }

    @Test
    void correctionUpdatesTheExistingRowAndTracksTheMarker() {
        buildFixture();
        UserAccount admin = fixtures.adminUser("marker");
        UserAccount teacherUser = fixtures.teacherUser("corrector");
        Teacher teacher = fixtures.teacherLinkedTo(teacherUser, "Corrector");
        fixtures.assignTeacher(assignment, teacher);
        LocalDate date = LocalDate.of(2026, 9, 10);
        ClassSession session = openSession(date, admin);
        Enrollment one = fixtures.activeEnrollment(fixtures.student("Corrected Student"), batch, LocalDate.of(2026, 9, 1));

        AttendanceForm first = new AttendanceForm();
        first.setRows(List.of(row(one.getId(), Attendance.Status.ABSENT, "")));
        attendanceService.record(session.getId(), first, admin);

        AttendanceForm correction = new AttendanceForm();
        correction.setRows(List.of(row(one.getId(), Attendance.Status.LATE, "Bus delay")));
        attendanceService.record(session.getId(), correction, teacherUser);

        List<Attendance> stored = attendanceRepository.findForSession(session.getId());
        assertThat(stored).hasSize(1);
        Attendance corrected = stored.get(0);
        assertThat(corrected.getStatus()).isEqualTo(Attendance.Status.LATE);
        assertThat(corrected.getRemarks()).isEqualTo("Bus delay");
        assertThat(corrected.getMarkedBy().getId()).isEqualTo(teacherUser.getId());
    }

    @Test
    void rejectsEnrollmentOfAnotherBatch() {
        buildFixture();
        UserAccount admin = fixtures.adminUser("marker");
        ClassSession session = openSession(LocalDate.of(2026, 9, 10), admin);

        Batch otherBatch = fixtures.batch(course, "Evening");
        Enrollment foreign = fixtures.activeEnrollment(fixtures.student("Foreign Student"), otherBatch,
                LocalDate.of(2026, 9, 1));

        AttendanceForm form = new AttendanceForm();
        form.setRows(List.of(row(foreign.getId(), Attendance.Status.PRESENT, "")));

        assertThatThrownBy(() -> attendanceService.record(session.getId(), form, admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must belong to this session's batch");
    }

    @Test
    void rejectsStudentsNotOnTheRosterOnTheSessionDate() {
        buildFixture();
        UserAccount admin = fixtures.adminUser("marker");
        LocalDate date = LocalDate.of(2026, 9, 10);
        ClassSession session = openSession(date, admin);

        // Joins the batch the day AFTER the session.
        Enrollment future = fixtures.activeEnrollment(fixtures.student("Future Student"), batch,
                LocalDate.of(2026, 9, 11));
        // Left the batch the day BEFORE the session.
        Enrollment past = fixtures.activeEnrollment(fixtures.student("Past Student"), batch,
                LocalDate.of(2026, 9, 1));
        past.setStatus(Enrollment.Status.WITHDRAWN);
        past.setEndDate(LocalDate.of(2026, 9, 9));

        AttendanceForm form = new AttendanceForm();
        form.setRows(List.of(row(future.getId(), Attendance.Status.PRESENT, "")));
        assertThatThrownBy(() -> attendanceService.record(session.getId(), form, admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("was not on this batch's roster");

        AttendanceForm pastForm = new AttendanceForm();
        pastForm.setRows(List.of(row(past.getId(), Attendance.Status.PRESENT, "")));
        assertThatThrownBy(() -> attendanceService.record(session.getId(), pastForm, admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("was not on this batch's roster");
    }

    @Test
    void cancelledSessionsRejectAttendance() {
        buildFixture();
        UserAccount admin = fixtures.adminUser("marker");
        ClassSession session = openSession(LocalDate.of(2026, 9, 10), admin);
        sessionService.cancel(session.getId(), admin);
        Enrollment one = fixtures.activeEnrollment(fixtures.student("Any Student"), batch, LocalDate.of(2026, 9, 1));

        AttendanceForm form = new AttendanceForm();
        form.setRows(List.of(row(one.getId(), Attendance.Status.PRESENT, "")));

        assertThatThrownBy(() -> attendanceService.record(session.getId(), form, admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cancelled session");
    }

    @Test
    void teacherCannotChangeAttendanceAfterCompletionButAdminCan() {
        buildFixture();
        UserAccount admin = fixtures.adminUser("marker");
        UserAccount teacherUser = fixtures.teacherUser("session.teacher");
        Teacher teacher = fixtures.teacherLinkedTo(teacherUser, "Session Teacher");
        fixtures.assignTeacher(assignment, teacher);
        ClassSession session = openSession(LocalDate.of(2026, 9, 10), admin);
        Enrollment one = fixtures.activeEnrollment(fixtures.student("Locked Student"), batch, LocalDate.of(2026, 9, 1));

        AttendanceForm form = new AttendanceForm();
        form.setRows(List.of(row(one.getId(), Attendance.Status.PRESENT, "")));
        attendanceService.record(session.getId(), form, teacherUser);
        sessionService.complete(session.getId(), teacherUser);

        AttendanceForm teacherChange = new AttendanceForm();
        teacherChange.setRows(List.of(row(one.getId(), Attendance.Status.ABSENT, "")));
        assertThatThrownBy(() -> attendanceService.record(session.getId(), teacherChange, teacherUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("can only be corrected by an administrator");

        AttendanceForm adminCorrection = new AttendanceForm();
        adminCorrection.setRows(List.of(row(one.getId(), Attendance.Status.EXCUSED, "School event")));
        int saved = attendanceService.record(session.getId(), adminCorrection, admin);
        assertThat(saved).isEqualTo(1);
        assertThat(attendanceRepository.findForSession(session.getId()).get(0).getStatus())
                .isEqualTo(Attendance.Status.EXCUSED);
    }

    @Test
    void percentageFormulaMatchesTheSpecification() {
        // (present + late) / (present + late + absent) * 100, excused excluded.
        assertThat(AttendanceService.percentage(8, 2, 0)).isEqualByComparingTo(new BigDecimal("100.0"));
        assertThat(AttendanceService.percentage(7, 1, 2)).isEqualByComparingTo(new BigDecimal("80.0"));
        assertThat(AttendanceService.percentage(0, 0, 10)).isEqualByComparingTo(new BigDecimal("0.0"));
        // Excused records do not dilute the percentage.
        assertThat(AttendanceService.percentage(9, 1, 0)).isEqualByComparingTo(new BigDecimal("100.0"));
        // Empty denominator: no percentage at all instead of a fake value.
        assertThat(AttendanceService.percentage(0, 0, 0)).isNull();
    }
}
