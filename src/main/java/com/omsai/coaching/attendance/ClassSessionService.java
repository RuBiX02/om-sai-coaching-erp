package com.omsai.coaching.attendance;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.security.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class-session business rules. A class session belongs to one
 * existing batch-subject assignment; it is a single class on a single
 * date, never a recurring timetable. Sessions are never deleted:
 * cancelling or completing preserves the record.
 */
@Service
public class ClassSessionService {

    private final ClassSessionRepository sessionRepository;
    private final BatchSubjectRepository batchSubjectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final SessionAuthority authority;

    public ClassSessionService(ClassSessionRepository sessionRepository,
                               BatchSubjectRepository batchSubjectRepository,
                               EnrollmentRepository enrollmentRepository,
                               AttendanceRepository attendanceRepository,
                               SessionAuthority authority) {
        this.sessionRepository = sessionRepository;
        this.batchSubjectRepository = batchSubjectRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.authority = authority;
    }

    /**
     * Creates a session for a batch-subject assignment. Rules:
     * - the assignment, its batch and its course must exist and be active;
     * - a teacher may only create sessions for subjects assigned to them,
     *   their profile being active;
     * - when both times are given, the end time must be after the start
     *   time.
     */
    @Transactional
    public ClassSession create(SessionForm form, UserAccount user) {
        BatchSubject assignment = batchSubjectRepository.findById(form.getBatchSubjectId())
                .orElseThrow(() -> new BusinessRuleException("This batch subject assignment does not exist."));
        authority.requireCanManage(assignment, user);

        Batch batch = assignment.getBatch();
        if (!batch.isActive()) {
            throw new BusinessRuleException("Batch \"" + batch.getName() + "\" is inactive, so sessions cannot be recorded for it.");
        }
        Course course = batch.getCourse();
        if (!course.isActive()) {
            throw new BusinessRuleException(
                    "Course \"" + course.getName() + "\" is inactive, so its batches cannot have new class sessions.");
        }
        validateTimes(form.getStartTime(), form.getEndTime());

        ClassSession session = new ClassSession();
        session.setBatchSubject(assignment);
        session.setSessionDate(form.getSessionDate());
        session.setStartTime(form.getStartTime());
        session.setEndTime(form.getEndTime());
        session.setTopic(trimToNull(form.getTopic()));
        session.setNotes(trimToNull(form.getNotes()));
        session.setStatus(ClassSession.Status.OPEN);
        session.setCreatedBy(user);
        return sessionRepository.save(session);
    }

    /**
     * Edits an OPEN session. Completed and cancelled sessions are
     * historical and cannot be edited. The batch-subject assignment of
     * an existing session never changes so history stays consistent.
     */
    @Transactional
    public ClassSession update(Long sessionId, SessionForm form, UserAccount user) {
        ClassSession session = getAuthorized(sessionId, user);
        if (!session.isOpen()) {
            throw new BusinessRuleException(
                    "Only OPEN sessions can be edited (this one is " + session.getStatus() + ").");
        }
        authority.requireCanManage(session.getBatchSubject(), user);
        validateTimes(form.getStartTime(), form.getEndTime());
        session.setSessionDate(form.getSessionDate());
        session.setStartTime(form.getStartTime());
        session.setEndTime(form.getEndTime());
        session.setTopic(trimToNull(form.getTopic()));
        session.setNotes(trimToNull(form.getNotes()));
        return sessionRepository.save(session);
    }

    /** Marks an OPEN session as COMPLETED. Allowed for admins and the assigned teacher. */
    @Transactional
    public ClassSession complete(Long sessionId, UserAccount user) {
        ClassSession session = getAuthorized(sessionId, user);
        if (!session.isOpen()) {
            throw new BusinessRuleException(
                    "Only OPEN sessions can be completed (this one is " + session.getStatus() + ").");
        }
        session.setStatus(ClassSession.Status.COMPLETED);
        return sessionRepository.save(session);
    }

    /**
     * Cancels an OPEN session while preserving its record. A session
     * that already has attendance rows cannot be cancelled: attendance
     * rows are never deleted and must stay attached to a class that
     * actually took place.
     */
    @Transactional
    public ClassSession cancel(Long sessionId, UserAccount user) {
        ClassSession session = getAuthorized(sessionId, user);
        if (!session.isOpen()) {
            throw new BusinessRuleException(
                    "Only OPEN sessions can be cancelled (this one is " + session.getStatus() + ").");
        }
        if (attendanceRepository.existsByClassSessionId(sessionId)) {
            throw new BusinessRuleException(
                    "This session already has attendance records, so it cannot be cancelled. Complete it instead.");
        }
        session.setStatus(ClassSession.Status.CANCELLED);
        return sessionRepository.save(session);
    }

    /** Fetches a session with full details, enforcing teacher ownership. */
    @Transactional(readOnly = true)
    public ClassSession getAuthorized(Long sessionId, UserAccount user) {
        ClassSession session = sessionRepository.findWithDetails(sessionId)
                .orElseThrow(() -> new BusinessRuleException("Class session not found."));
        authority.requireCanView(session, user);
        return session;
    }

    /**
     * The eligible roster for a session: every enrollment of the
     * session's batch whose effective period covers the session date.
     * A student who enrolled after the session is not included; a
     * student whose enrollment ended before the session is not
     * included. Existing attendance is attached so corrections
     * prefill the form.
     */
    @Transactional(readOnly = true)
    public List<AttendanceMarkRow> rosterFor(ClassSession session) {
        LocalDate date = session.getSessionDate();
        List<Enrollment> enrollments = enrollmentRepository.findRosterForBatchOnDate(
                session.getBatchSubject().getBatch().getId(), date);
        List<Attendance> attendance = attendanceRepository.findForSession(session.getId());
        Map<Long, Attendance> byEnrollment = new HashMap<>();
        for (Attendance row : attendance) {
            byEnrollment.put(row.getEnrollment().getId(), row);
        }
        return enrollments.stream()
                .map(enrollment -> {
                    Attendance existing = byEnrollment.get(enrollment.getId());
                    return new AttendanceMarkRow(
                            enrollment.getId(),
                            enrollment.getStudent().getStudentCode(),
                            enrollment.getStudent().getFullName(),
                            existing == null ? null : existing.getStatus(),
                            existing == null || existing.getRemarks() == null ? "" : existing.getRemarks());
                })
                .toList();
    }

    /** Batch-subject assignments eligible for NEW sessions (active batch + course). */
    @Transactional(readOnly = true)
    public List<BatchSubject> assignableList(UserAccount user) {
        if (authority.isTeacher(user)) {
            // Teachers may only create sessions for their own active assignments.
            List<BatchSubject> own = batchSubjectRepository.findForUser(user.getId());
            return own.stream()
                    .filter(bs -> bs.getBatch().isActive() && bs.getBatch().getCourse().isActive())
                    .toList();
        }
        return batchSubjectRepository.findForActiveBatches();
    }

    @Transactional(readOnly = true)
    public Page<ClassSession> search(Long batchId, Long subjectId, LocalDate from, LocalDate to,
                                     ClassSession.Status status, Pageable pageable) {
        if (batchId != null) {
            return sessionRepository.searchForBatch(batchId, from, to, status, pageable);
        }
        return sessionRepository.search(batchId, subjectId, from, to, status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ClassSession> searchForTeacher(Long teacherId, LocalDate from, LocalDate to,
                                               ClassSession.Status status, Pageable pageable) {
        return sessionRepository.searchForTeacher(teacherId, from, to, status, pageable);
    }

    public int totalSessions() {
        return (int) sessionRepository.count();
    }

    /**
     * Displays a status like OPEN/COMPLETED/CANCELLED along with the
     * reason text used by the views.
     */
    public static String statusLabel(ClassSession.Status status) {
        return switch (status) {
            case OPEN -> "Open";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
        };
    }

    // ----- helpers ----------------------------------------------------------

    private static void validateTimes(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new BusinessRuleException("End time must be after the start time.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
