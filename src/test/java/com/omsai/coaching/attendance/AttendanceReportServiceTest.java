package com.omsai.coaching.attendance;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.CourseSubject;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
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
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Report rules: only COMPLETED sessions count, denominators follow the
 * enrollment's effective period (not the student's current batch),
 * cancelled and open sessions are excluded, excused marks never dilute
 * the percentage, and transfers keep every period visible in the
 * student's history.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceReportServiceTest {

    @Autowired
    private AttendanceReportService reportService;

    @Autowired
    private ClassSessionService sessionService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private Fixtures fixtures;

    private Course course;
    private Batch morningBatch;
    private Batch eveningBatch;
    private BatchSubject morningAssignment;
    private BatchSubject eveningAssignment;
    private UserAccount admin;

    private void buildFixture() {
        course = fixtures.course("Report Course");
        morningBatch = fixtures.batch(course, "Morning");
        eveningBatch = fixtures.batch(course, "Evening");
        CourseSubject courseSubject = fixtures.assign(course, fixtures.subject("Report Subject"));
        morningAssignment = fixtures.assignCourseSubject(morningBatch, courseSubject);
        eveningAssignment = fixtures.assignCourseSubject(eveningBatch, courseSubject);
        admin = fixtures.adminUser("reporter");
    }

    private ClassSession session(BatchSubject assignment, LocalDate date) {
        SessionForm form = new SessionForm();
        form.setBatchSubjectId(assignment.getId());
        form.setSessionDate(date);
        form.setStartTime(LocalTime.of(9, 0));
        return sessionService.create(form, admin);
    }

    /** Records one attendance status for one enrollment on one session. */
    private void mark(ClassSession session, Enrollment enrollment, Attendance.Status status) {
        AttendanceForm form = new AttendanceForm();
        AttendanceForm.Row row = new AttendanceForm.Row();
        row.setEnrollmentId(enrollment.getId());
        row.setStatus(status);
        form.setRows(List.of(row));
        attendanceService.record(session.getId(), form, admin);
    }

    private void completed(ClassSession session) {
        sessionService.complete(session.getId(), admin);
    }

    private AttendanceReportRow rowFor(List<AttendanceReportRow> rows, String fullNamePrefix) {
        return rows.stream()
                .filter(r -> r.studentName().startsWith(fullNamePrefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No report row for " + fullNamePrefix));
    }

    @Test
    void batchReportCountsOnlyCompletedSessionsInsideEachEnrollmentPeriod() {
        buildFixture();

        Enrollment early = fixtures.activeEnrollment(fixtures.student("Early Student"), morningBatch,
                LocalDate.of(2026, 9, 1));
        Enrollment lateJoiner = fixtures.activeEnrollment(fixtures.student("Late Joiner"), morningBatch,
                LocalDate.of(2026, 9, 15));
        Enrollment departed = fixtures.activeEnrollment(fixtures.student("Departed Student"), morningBatch,
                LocalDate.of(2026, 9, 1));
        departed.setStatus(Enrollment.Status.WITHDRAWN);
        departed.setEndDate(LocalDate.of(2026, 9, 9));

        ClassSession first = session(morningAssignment, LocalDate.of(2026, 9, 10));
        mark(first, early, Attendance.Status.PRESENT);
        completed(first);

        ClassSession second = session(morningAssignment, LocalDate.of(2026, 9, 20));
        mark(second, early, Attendance.Status.ABSENT);
        mark(second, lateJoiner, Attendance.Status.PRESENT);
        completed(second);

        session(morningAssignment, LocalDate.of(2026, 9, 25));             // left OPEN on purpose
        ClassSession cancelled = session(morningAssignment, LocalDate.of(2026, 9, 26));
        sessionService.cancel(cancelled.getId(), admin);                   // cancelled, no attendance

        List<AttendanceReportRow> rows = reportService.forBatch(morningBatch.getId(), null, null);

        AttendanceReportRow earlyRow = rowFor(rows, early.getStudent().getFullName());
        assertThat(earlyRow.eligibleSessions()).isEqualTo(2);
        assertThat(earlyRow.present()).isEqualTo(1);
        assertThat(earlyRow.absent()).isEqualTo(1);
        assertThat(earlyRow.percentage()).isEqualByComparingTo(new BigDecimal("50.0"));

        AttendanceReportRow lateRow = rowFor(rows, lateJoiner.getStudent().getFullName());
        assertThat(lateRow.eligibleSessions()).isEqualTo(1);
        assertThat(lateRow.present()).isEqualTo(1);
        assertThat(lateRow.percentage()).isEqualByComparingTo(new BigDecimal("100.0"));

        // Left before any completed session: the student is never on the
        // roster of a counted session, so no report row exists at all
        // (showing a fabricated 0% would misrepresent "no data").
        assertThat(rows.stream()
                .filter(r -> r.studentName().startsWith(departed.getStudent().getFullName()))
                .count()).isZero();
    }

    @Test
    void excusedMarksDoNotLowerThePercentageAndProduceNoFakeZero() {
        buildFixture();
        Enrollment excused = fixtures.activeEnrollment(fixtures.student("Excused Student"), morningBatch,
                LocalDate.of(2026, 9, 1));
        Enrollment onlyExcused = fixtures.activeEnrollment(fixtures.student("Only Excused Student"), morningBatch,
                LocalDate.of(2026, 9, 1));

        ClassSession held1 = session(morningAssignment, LocalDate.of(2026, 9, 10));
        mark(held1, excused, Attendance.Status.EXCUSED);
        mark(held1, onlyExcused, Attendance.Status.EXCUSED);
        completed(held1);

        ClassSession held2 = session(morningAssignment, LocalDate.of(2026, 9, 20));
        mark(held2, excused, Attendance.Status.PRESENT);
        completed(held2);

        List<AttendanceReportRow> rows = reportService.forBatch(morningBatch.getId(), null, null);

        AttendanceReportRow row = rowFor(rows, "Excused Student");
        assertThat(row.eligibleSessions()).isEqualTo(2);
        assertThat(row.present()).isEqualTo(1);
        assertThat(row.excused()).isEqualTo(1);
        // denominator = present + late + absent = 1, so the percentage stays 100.
        assertThat(row.percentage()).isEqualByComparingTo(new BigDecimal("100.0"));

        // This student was marked EXCUSED only, but was on the roster for
        // both held sessions: two eligible sessions, zero evaluated marks,
        // so the percentage is null ("insufficient data"), never a fake 0.
        AttendanceReportRow onlyRow = rowFor(rows, "Only Excused Student");
        assertThat(onlyRow.eligibleSessions()).isEqualTo(2);
        assertThat(onlyRow.excused()).isEqualTo(1);
        assertThat(onlyRow.hasData()).isFalse();
        assertThat(onlyRow.percentage()).isNull();
    }

    @Test
    void studentReportKeepsEveryEnrollmentPeriodAfterATransfer() {
        buildFixture();
        Enrollment original = fixtures.activeEnrollment(fixtures.student("Transferred Student"), morningBatch,
                LocalDate.of(2026, 9, 1));

        ClassSession inMorning = session(morningAssignment, LocalDate.of(2026, 9, 10));
        mark(inMorning, original, Attendance.Status.LATE);
        completed(inMorning);

        TransferForm transfer = new TransferForm();
        transfer.setStudentId(original.getStudent().getId());
        transfer.setTargetBatchId(eveningBatch.getId());
        transfer.setTransferDate(LocalDate.of(2026, 9, 15));
        Enrollment moved = enrollmentService.transfer(transfer);

        ClassSession inEvening = session(eveningAssignment, LocalDate.of(2026, 9, 20));
        mark(inEvening, moved, Attendance.Status.PRESENT);
        completed(inEvening);

        ClassSession inEvening2 = session(eveningAssignment, LocalDate.of(2026, 9, 21));
        mark(inEvening2, moved, Attendance.Status.ABSENT);
        completed(inEvening2);

        var report = reportService.forStudent(original.getStudent().getId(), null, null);
        assertThat(report.rows()).hasSize(2);

        AttendanceReportRow oldRow = report.rows().stream()
                .filter(r -> r.batchName().startsWith("Morning"))
                .findFirst().orElseThrow();
        assertThat(oldRow.enrolledFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(oldRow.enrolledTo()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(oldRow.eligibleSessions()).isEqualTo(1);
        assertThat(oldRow.late()).isEqualTo(1);
        assertThat(oldRow.percentage()).isEqualByComparingTo(new BigDecimal("100.0"));

        AttendanceReportRow newRow = report.rows().stream()
                .filter(r -> r.batchName().startsWith("Evening"))
                .findFirst().orElseThrow();
        assertThat(newRow.enrolledFrom()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(newRow.enrolledTo()).isNull();
        assertThat(newRow.eligibleSessions()).isEqualTo(2);
        assertThat(newRow.present()).isEqualTo(1);
        assertThat(newRow.absent()).isEqualTo(1);
        assertThat(newRow.percentage()).isEqualByComparingTo(new BigDecimal("50.0"));

        // The aggregate across periods: 1 present + 1 late + 1 absent -> (2/3)*100.
        assertThat(report.overallPercentage()).isEqualByComparingTo(new BigDecimal("66.7"));
    }

    @Test
    void dateReportListsEverySessionWithRecordedCounts() {
        buildFixture();
        Enrollment one = fixtures.activeEnrollment(fixtures.student("Day Student One"), morningBatch,
                LocalDate.of(2026, 9, 1));
        Enrollment two = fixtures.activeEnrollment(fixtures.student("Day Student Two"), eveningBatch,
                LocalDate.of(2026, 9, 1));

        ClassSession session1 = session(morningAssignment, LocalDate.of(2026, 9, 10));
        mark(session1, one, Attendance.Status.PRESENT);          // left OPEN: still listed, mark counts

        ClassSession session2 = session(eveningAssignment, LocalDate.of(2026, 9, 10));
        mark(session2, two, Attendance.Status.LATE);

        session(morningAssignment, LocalDate.of(2026, 9, 10));   // no attendance recorded

        var report = reportService.forDate(LocalDate.of(2026, 9, 10));
        assertThat(report.rows()).hasSize(3);
        assertThat(report.rows().stream().filter(r -> r.present() == 1).count()).isEqualTo(1);
        assertThat(report.rows().stream().filter(r -> r.late() == 1).count()).isEqualTo(1);
        assertThat(report.rows().stream().filter(r -> r.present() + r.late() + r.absent() + r.excused() == 0).count())
                .isEqualTo(1);
    }
}
