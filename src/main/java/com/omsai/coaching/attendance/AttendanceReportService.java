package com.omsai.coaching.attendance;

import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds attendance-report rows from database-backed aggregate
 * queries. Denominators count only COMPLETED sessions inside the
 * enrollment's effective period; cancelled and open sessions never
 * inflate anything, and students are evaluated by the period they were
 * actually on the roster — never by their current batch.
 */
@Service
public class AttendanceReportService {

    private final AttendanceRepository attendanceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassSessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;

    public AttendanceReportService(AttendanceRepository attendanceRepository,
                                   EnrollmentRepository enrollmentRepository,
                                   ClassSessionRepository sessionRepository,
                                   StudentRepository studentRepository,
                                   BatchRepository batchRepository) {
        this.attendanceRepository = attendanceRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional(readOnly = true)
    public List<AttendanceReportRow> forBatch(Long batchId, LocalDate from, LocalDate to) {
        Map<Long, EnrollmentAttendanceRow> counts = index(
                attendanceRepository.attendanceCountsForBatch(batchId, from, to));
        return merge(attendanceRepository.sessionCountsForBatch(batchId, from, to), counts);
    }

    @Transactional(readOnly = true)
    public List<AttendanceReportRow> forBatchSubject(Long batchSubjectId, LocalDate from, LocalDate to) {
        Map<Long, EnrollmentAttendanceRow> counts = index(
                attendanceRepository.attendanceCountsForBatchSubject(batchSubjectId, from, to));
        return merge(attendanceRepository.sessionCountsForBatchSubject(batchSubjectId, from, to), counts);
    }

    /**
     * The individual student report: one row per historical enrollment
     * (so batch transfers keep their history visible), plus the
     * aggregate across all rows.
     */
    @Transactional(readOnly = true)
    public StudentReport forStudent(Long studentId, LocalDate from, LocalDate to) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessRuleException("Student not found."));
        Map<Long, EnrollmentAttendanceRow> counts = index(
                attendanceRepository.attendanceCountsForStudent(studentId, from, to));
        Map<Long, EnrollmentSessionRow> denominators = new HashMap<>();
        for (EnrollmentSessionRow row : attendanceRepository.sessionCountsForStudent(studentId, from, to)) {
            denominators.put(row.getEnrollmentId(), row);
        }
        List<AttendanceReportRow> rows = enrollmentRepository.findForStudent(studentId).stream()
                .map(enrollment -> toRow(enrollment,
                        denominators.get(enrollment.getId()),
                        counts.get(enrollment.getId())))
                .toList();
        return new StudentReport(student, rows);
    }

    /** One date: every session of that date with recorded statuses. */
    @Transactional(readOnly = true)
    public DateReport forDate(LocalDate date) {
        Map<Long, SessionCountRow> counts = new HashMap<>();
        for (SessionCountRow row : attendanceRepository.countRowsForDate(date)) {
            counts.put(row.getSessionId(), row);
        }
        List<ClassSession> sessions = sessionRepository.findForDate(date);
        List<SessionDayRow> rows = sessions.stream()
                .map(session -> {
                    SessionCountRow count = counts.get(session.getId());
                    return new SessionDayRow(session,
                            count == null ? 0 : count.getPresentCount(),
                            count == null ? 0 : count.getAbsentCount(),
                            count == null ? 0 : count.getLateCount(),
                            count == null ? 0 : count.getExcusedCount());
                })
                .toList();
        return new DateReport(rows);
    }

    private List<AttendanceReportRow> merge(List<EnrollmentSessionRow> denominators,
                                            Map<Long, EnrollmentAttendanceRow> counts) {
        return denominators.stream()
                .map(row -> toRow(null, row, counts.get(row.getEnrollmentId())))
                .toList();
    }

    private static AttendanceReportRow toRow(Enrollment enrollment,
                                             EnrollmentSessionRow denominator,
                                             EnrollmentAttendanceRow count) {
        long present = count == null ? 0 : count.getPresentCount();
        long absent = count == null ? 0 : count.getAbsentCount();
        long late = count == null ? 0 : count.getLateCount();
        long excused = count == null ? 0 : count.getExcusedCount();
        long sessions = denominator == null ? 0 : denominator.getSessionCount();
        BigDecimal percentage = AttendanceService.percentage(present, late, absent);
        if (enrollment != null) {
            return new AttendanceReportRow(
                    enrollment.getStudent().getStudentCode(),
                    enrollment.getStudent().getFullName(),
                    enrollment.getCourse().getName(),
                    enrollment.getBatch().getName(),
                    enrollment.getEnrollmentDate(),
                    enrollment.getEndDate(),
                    sessions, present, absent, late, excused, percentage);
        }
        return new AttendanceReportRow(
                denominator.getStudentCode(),
                denominator.getStudentName(),
                null,
                null,
                denominator.getEnrolledFrom(),
                denominator.getEnrolledTo(),
                sessions, present, absent, late, excused, percentage);
    }

    private static Map<Long, EnrollmentAttendanceRow> index(List<EnrollmentAttendanceRow> rows) {
        Map<Long, EnrollmentAttendanceRow> map = new HashMap<>();
        for (EnrollmentAttendanceRow row : rows) {
            map.put(row.getEnrollmentId(), row);
        }
        return map;
    }

    // ----- view wrappers ----------------------------------------------------

    public record StudentReport(Student student, List<AttendanceReportRow> rows) {
        public BigDecimal overallPercentage() {
            long[] t = AttendanceReportRow.aggregate(rows);
            return AttendanceService.percentage(t[0], t[1], t[2]);
        }
    }

    public record DateReport(List<SessionDayRow> rows) {
    }

    /**
     * One session on the date-view of the report: the session (with
     * batch, subject and teacher pre-fetched) and the statuses
     * recorded for it.
     */
    public record SessionDayRow(ClassSession session, long present, long absent, long late, long excused) {
    }
}
