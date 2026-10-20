package com.omsai.coaching.dashboard;

import com.omsai.coaching.attendance.AttendanceRepository;
import com.omsai.coaching.attendance.ClassSessionRepository;
import com.omsai.coaching.attendance.SessionCountRow;
import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.course.CourseRepository;
import com.omsai.coaching.course.Course.Status;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.fees.FeeAccountRepository;
import com.omsai.coaching.fees.PaymentRepository;
import com.omsai.coaching.student.StudentRepository;
import com.omsai.coaching.teacher.TeacherRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Figures for the administrator dashboard. All values come from the
 * database - nothing is hardcoded. Phase 2 adds today's sessions and
 * attendance, the current month's collections, the total outstanding
 * balance and the number of overdue fee accounts, all computed within
 * the business timezone.
 */
@Service
public class DashboardService {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final BatchRepository batchRepository;
    private final TeacherRepository teacherRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassSessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final PaymentRepository paymentRepository;
    private final FeeAccountRepository feeAccountRepository;
    private final BusinessClock businessClock;

    public DashboardService(StudentRepository studentRepository,
                            CourseRepository courseRepository,
                            BatchRepository batchRepository,
                            TeacherRepository teacherRepository,
                            EnrollmentRepository enrollmentRepository,
                            ClassSessionRepository sessionRepository,
                            AttendanceRepository attendanceRepository,
                            PaymentRepository paymentRepository,
                            FeeAccountRepository feeAccountRepository,
                            BusinessClock businessClock) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.batchRepository = batchRepository;
        this.teacherRepository = teacherRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.paymentRepository = paymentRepository;
        this.feeAccountRepository = feeAccountRepository;
        this.businessClock = businessClock;
    }

    public record Stats(long activeStudents, long activeCourses, long activeBatches, long activeTeachers) {
    }

    /** Attendance totals for a single date (dashboard summary). */
    public record AttendanceTotals(long present, long late, long absent, long excused) {
    }

    /**
     * Phase 2 figures. Reporting ranges: "today" is the business-timezone
     * calendar day; monthly collections cover the 1st to the last day of
     * the current business month; overdue counts accounts whose due date
     * has passed with a balance still outstanding.
     */
    public record PhaseTwoStats(long sessionsToday,
                                long openToday,
                                long completedToday,
                                long cancelledToday,
                                AttendanceTotals attendanceToday,
                                BigDecimal collectionsThisMonth,
                                BigDecimal outstandingTotal,
                                long overdueAccounts) {
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        return new Stats(
                studentRepository.countByActiveTrue(),
                courseRepository.countByStatus(Status.ACTIVE),
                batchRepository.countByStatus(com.omsai.coaching.batch.Batch.Status.ACTIVE),
                teacherRepository.countByActiveTrue());
    }

    /** Five most recent enrollments for the dashboard table. */
    @Transactional(readOnly = true)
    public List<Enrollment> recentEnrollments() {
        return enrollmentRepository.findRecent(PageRequest.of(0, 5));
    }

    /** Sessions recorded for today (any status), for the dashboard list. */
    @Transactional(readOnly = true)
    public List<com.omsai.coaching.attendance.ClassSession> sessionsToday() {
        return sessionRepository.findForDate(businessClock.today());
    }

    @Transactional(readOnly = true)
    public PhaseTwoStats phaseTwoStats() {
        java.time.LocalDate today = businessClock.today();
        long sessions = sessionRepository.countBySessionDate(today);
        YearMonth month = businessClock.currentMonth();
        long present = 0, late = 0, absent = 0, excused = 0;
        for (SessionCountRow row : attendanceRepository.countRowsForDate(today)) {
            present += row.getPresentCount();
            late += row.getLateCount();
            absent += row.getAbsentCount();
            excused += row.getExcusedCount();
        }
        return new PhaseTwoStats(
                sessions,
                sessionRepository.countBySessionDateAndStatus(today, com.omsai.coaching.attendance.ClassSession.Status.OPEN),
                sessionRepository.countBySessionDateAndStatus(today, com.omsai.coaching.attendance.ClassSession.Status.COMPLETED),
                sessionRepository.countBySessionDateAndStatus(today, com.omsai.coaching.attendance.ClassSession.Status.CANCELLED),
                new AttendanceTotals(present, late, absent, excused),
                paymentRepository.receivedBetween(month.atDay(1), month.atEndOfMonth()),
                feeAccountRepository.totalOutstanding(),
                feeAccountRepository.countOverdue(today));
    }
}
