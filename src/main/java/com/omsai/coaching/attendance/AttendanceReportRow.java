package com.omsai.coaching.attendance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of an attendance report, with all the counts and the
 * derived percentage. All numbers come from database aggregation, and
 * the percentage follows the documented formula
 * (present + late) / (present + late + absent) * 100 — excused
 * excluded from the denominator; null when there is no data.
 */
public record AttendanceReportRow(String studentCode,
                                  String studentName,
                                  String courseName,
                                  String batchName,
                                  LocalDate enrolledFrom,
                                  LocalDate enrolledTo,
                                  long eligibleSessions,
                                  long present,
                                  long absent,
                                  long late,
                                  long excused,
                                  BigDecimal percentage) {

    /** True when at least one attendance status (excluding excused) was evaluated. */
    public boolean hasData() {
        return present + late + absent > 0;
    }

    /** Aggregate columns across rows: {present, late, absent, excused}. */
    public static long[] aggregate(java.util.List<AttendanceReportRow> rows) {
        long present = 0, absent = 0, late = 0, excused = 0;
        for (AttendanceReportRow row : rows) {
            present += row.present();
            absent += row.absent();
            late += row.late();
            excused += row.excused();
        }
        return new long[]{present, late, absent, excused};
    }

    public static BigDecimal percentage(long present, long late, long absent) {
        return AttendanceService.percentage(present, late, absent);
    }
}
