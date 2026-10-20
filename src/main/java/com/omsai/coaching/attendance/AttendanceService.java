package com.omsai.coaching.attendance;

import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.security.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Attendance business rules. Attendance references the student's
 * enrollment and is only accepted for enrollments of the session's
 * batch whose effective period covers the session date. Recording a
 * row for an enrollment that already has one updates ("corrects") the
 * existing row: duplicates are impossible, rows are never deleted.
 */
@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassSessionService sessionService;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             EnrollmentRepository enrollmentRepository,
                             ClassSessionService sessionService) {
        this.attendanceRepository = attendanceRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionService = sessionService;
    }

    /**
     * Records (or corrects) a whole roster of attendance rows in one
     * submission. Rules:
     * - attendance is never recorded against a CANCELLED session;
     * - a teacher may record attendance only while the session is OPEN,
     *   and only for their own session;
     * - an administrator may also correct attendance of COMPLETED
     *   sessions (the marker and time of recording are tracked);
     * - every submitted enrollment is re-validated server side: it must
     *   belong to the session's batch and its effective period must
     *   cover the session date.
     */
    @Transactional
    public int record(Long sessionId, AttendanceForm form, UserAccount marker) {
        ClassSession session = sessionService.getAuthorized(sessionId, marker);
        if (session.isCancelled()) {
            throw new BusinessRuleException("Attendance cannot be recorded for a cancelled session.");
        }
        if (session.isCompleted() && marker.getRole() == UserAccount.Role.TEACHER) {
            throw new BusinessRuleException(
                    "This session is completed. Attendance can only be corrected by an administrator.");
        }
        List<AttendanceForm.Row> rows = form.getRows();
        Set<Long> seen = new HashSet<>();
        LocalDate date = session.getSessionDate();
        Long batchId = session.getBatchSubject().getBatch().getId();
        int saved = 0;
        for (AttendanceForm.Row row : rows) {
            if (row.getEnrollmentId() == null || !seen.add(row.getEnrollmentId())) {
                continue;
            }
            saved = saveRow(saved, row, session, marker, batchId, date);
        }
        if (saved == 0) {
            throw new BusinessRuleException("No attendance rows were submitted.");
        }
        return saved;
    }

    private int saveRow(int saved, AttendanceForm.Row row, ClassSession session, UserAccount marker,
                        Long batchId, LocalDate date) {
        Enrollment enrollment = enrollmentRepository.findById(row.getEnrollmentId())
                .orElseThrow(() -> new BusinessRuleException(
                        "Enrollment " + row.getEnrollmentId() + " does not exist."));
        if (!enrollment.getBatch().getId().equals(batchId)) {
            throw new BusinessRuleException(
                    "Attendance entries must belong to this session's batch (student "
                            + enrollment.getStudent().getStudentCode() + " is enrolled elsewhere).");
        }
        if (enrollment.getEnrollmentDate().isAfter(date)
                || (enrollment.getEndDate() != null && enrollment.getEndDate().isBefore(date))) {
            throw new BusinessRuleException(
                    enrollment.getStudent().getFullName() + " was not on this batch's roster on "
                            + date + " (enrolled " + enrollment.getEnrollmentDate()
                            + (enrollment.getEndDate() == null ? ", still active" : " to " + enrollment.getEndDate()) + ").");
        }
        attendanceRepository.findByClassSessionIdAndEnrollmentId(session.getId(), enrollment.getId())
                .ifPresentOrElse(existing -> {
                    existing.setStatus(row.getStatus());
                    existing.setRemarks(trimToNull(row.getRemarks()));
                    existing.setMarkedBy(marker);
                }, () -> {
                    Attendance attendance = new Attendance();
                    attendance.setClassSession(session);
                    attendance.setEnrollment(enrollment);
                    attendance.setStatus(row.getStatus());
                    attendance.setRemarks(trimToNull(row.getRemarks()));
                    attendance.setMarkedBy(marker);
                    attendanceRepository.save(attendance);
                });
        return saved + 1;
    }

    /**
     * Attendance percentage per the documented formula:
     * (present + late) / (present + late + absent) * 100.
     * EXCUSED records are excluded from the denominator. A null result
     * means the denominator is empty (no evaluated attendance), so the
     * view shows "insufficient data" instead of a percentage.
     */
    public static BigDecimal percentage(long present, long late, long absent) {
        long denominator = present + late + absent;
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf((present + late) * 100L)
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
