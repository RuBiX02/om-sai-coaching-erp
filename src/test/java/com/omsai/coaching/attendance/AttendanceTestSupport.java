package com.omsai.coaching.attendance;

import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.security.UserAccount;

/**
 * Small helper for tests that need attendance rows without going
 * through the service layer (for example to set up a session that
 * already has records before testing cancellation rules).
 */
final class AttendanceTestSupport {

    private AttendanceTestSupport() {
    }

    static Attendance mark(AttendanceRepository repository,
                           ClassSession session,
                           Enrollment enrollment,
                           Attendance.Status status,
                           UserAccount marker) {
        Attendance attendance = new Attendance();
        attendance.setClassSession(session);
        attendance.setEnrollment(enrollment);
        attendance.setStatus(status);
        attendance.setMarkedBy(marker);
        return repository.save(attendance);
    }
}
