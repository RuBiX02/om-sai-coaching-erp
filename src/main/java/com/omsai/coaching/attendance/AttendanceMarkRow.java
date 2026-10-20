package com.omsai.coaching.attendance;

import com.omsai.coaching.enrollment.Enrollment;

/**
 * One roster line of the attendance marking page: a student's
 * enrollment on the session's batch, the currently recorded status
 * (null when unmarked) and any remarks.
 */
public record AttendanceMarkRow(Long enrollmentId,
                                String studentCode,
                                String studentName,
                                Attendance.Status currentStatus,
                                String currentRemarks) {

    public static AttendanceMarkRow from(Enrollment enrollment, Attendance.Status status, String remarks) {
        return new AttendanceMarkRow(enrollment.getId(),
                enrollment.getStudent().getStudentCode(),
                enrollment.getStudent().getFullName(),
                status, remarks == null ? "" : remarks);
    }
}
