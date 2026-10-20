package com.omsai.coaching.attendance;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.common.CsvWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Builds the downloadable CSV documents of the attendance reports.
 *
 * <p>The service never recalculates attendance: every count and every
 * percentage comes from {@link AttendanceReportService}, the very same
 * service the on-screen reports use, so a download can never disagree
 * with the page it was taken from. This class only decides which
 * columns are written and how the file is named.</p>
 *
 * <p>Export rules:</p>
 * <ul>
 *   <li>the filters of the report page (date range, batch, subject,
 *       date) are applied to the download as well - a download never
 *       contains records outside the selected filter;</li>
 *   <li>an undefined percentage (no evaluated attendance) is written as
 *       an empty field, never as 0;</li>
 *   <li>only completed sessions enter the numbers, and excused records
 *       stay out of the denominator, exactly as on the page;</li>
 *   <li>no internal ids, account data or fee information is exported -
 *       only attendance facts and the names needed to read them.</li>
 * </ul>
 */
@Service
public class AttendanceExportService {

    /** A finished download: a safe file name and the CSV text. */
    public record CsvDocument(String filename, String content) {
    }

    /** The batch report covers every subject of the batch. */
    private static final String ALL_SUBJECTS = "All subjects";

    private final AttendanceReportService reportService;
    private final BatchRepository batchRepository;
    private final BatchSubjectRepository batchSubjectRepository;

    public AttendanceExportService(AttendanceReportService reportService,
                                   BatchRepository batchRepository,
                                   BatchSubjectRepository batchSubjectRepository) {
        this.reportService = reportService;
        this.batchRepository = batchRepository;
        this.batchSubjectRepository = batchSubjectRepository;
    }

    /**
     * The individual student report: one line per enrollment period, so
     * batch transfers stay visible.
     */
    @Transactional(readOnly = true)
    public CsvDocument studentReport(Long studentId, LocalDate from, LocalDate to) {
        AttendanceReportService.StudentReport report = reportService.forStudent(studentId, from, to);
        CsvWriter csv = CsvWriter.create();
        csv.header("Student code",
                "Student name",
                "Course",
                "Batch",
                "Enrolled from",
                "Enrolled to",
                "Eligible completed sessions",
                "Present",
                "Absent",
                "Late",
                "Excused",
                "Attendance percentage");
        for (AttendanceReportRow row : report.rows()) {
            csv.row(row.studentCode(),
                    row.studentName(),
                    row.courseName(),
                    row.batchName(),
                    date(row.enrolledFrom()),
                    date(row.enrolledTo()),
                    String.valueOf(row.eligibleSessions()),
                    String.valueOf(row.present()),
                    String.valueOf(row.absent()),
                    String.valueOf(row.late()),
                    String.valueOf(row.excused()),
                    percentage(row));
        }
        return new CsvDocument("attendance-student-" + rangeLabel(from, to) + ".csv", csv.toCsv());
    }

    /** The batch report: every student of one batch over the date range. */
    @Transactional(readOnly = true)
    public CsvDocument batchReport(Long batchId, LocalDate from, LocalDate to) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessRuleException("Batch not found."));
        List<AttendanceReportRow> rows = reportService.forBatch(batchId, from, to);
        CsvWriter csv = CsvWriter.create();
        csv.header("Student code",
                "Student name",
                "Course",
                "Batch",
                "Subject",
                "Academic year",
                "Enrolled from",
                "Enrolled to",
                "Eligible completed sessions",
                "Present",
                "Absent",
                "Late",
                "Excused",
                "Attendance percentage");
        for (AttendanceReportRow row : rows) {
            csv.row(row.studentCode(),
                    row.studentName(),
                    batch.getCourse().getName(),
                    batch.getName(),
                    ALL_SUBJECTS,
                    batch.getAcademicYear(),
                    date(row.enrolledFrom()),
                    date(row.enrolledTo()),
                    String.valueOf(row.eligibleSessions()),
                    String.valueOf(row.present()),
                    String.valueOf(row.absent()),
                    String.valueOf(row.late()),
                    String.valueOf(row.excused()),
                    percentage(row));
        }
        return new CsvDocument("attendance-batch-" + rangeLabel(from, to) + ".csv", csv.toCsv());
    }

    /** The subject report: one subject within a specific batch. */
    @Transactional(readOnly = true)
    public CsvDocument subjectReport(Long batchId, Long courseSubjectId, LocalDate from, LocalDate to) {
        BatchSubject assignment = batchSubjectRepository
                .findByBatchIdAndCourseSubjectId(batchId, courseSubjectId)
                .orElseThrow(() -> new BusinessRuleException("This subject is not assigned to that batch."));
        List<AttendanceReportRow> rows = reportService.forBatchSubject(assignment.getId(), from, to);
        Batch batch = assignment.getBatch();
        CsvWriter csv = CsvWriter.create();
        csv.header("Student code",
                "Student name",
                "Course",
                "Batch",
                "Subject",
                "Academic year",
                "Enrolled from",
                "Enrolled to",
                "Eligible completed sessions",
                "Present",
                "Absent",
                "Late",
                "Excused",
                "Attendance percentage");
        for (AttendanceReportRow row : rows) {
            csv.row(row.studentCode(),
                    row.studentName(),
                    batch.getCourse().getName(),
                    batch.getName(),
                    assignment.getCourseSubject().getSubject().getName(),
                    batch.getAcademicYear(),
                    date(row.enrolledFrom()),
                    date(row.enrolledTo()),
                    String.valueOf(row.eligibleSessions()),
                    String.valueOf(row.present()),
                    String.valueOf(row.absent()),
                    String.valueOf(row.late()),
                    String.valueOf(row.excused()),
                    percentage(row));
        }
        return new CsvDocument("attendance-subject-" + rangeLabel(from, to) + ".csv", csv.toCsv());
    }

    /**
     * The date report: one line per recorded attendance entry of that
     * date. Sessions without recorded attendance contribute no lines.
     */
    @Transactional(readOnly = true)
    public CsvDocument dateReport(LocalDate date) {
        List<AttendanceReportService.DateAttendanceRow> rows = reportService.attendanceRowsForDate(date);
        CsvWriter csv = CsvWriter.create();
        csv.header("Class date",
                "Batch",
                "Subject",
                "Teacher",
                "Student code",
                "Student name",
                "Attendance status",
                "Remarks");
        for (AttendanceReportService.DateAttendanceRow row : rows) {
            csv.row(date(row.sessionDate()),
                    row.batchName(),
                    row.subjectName(),
                    row.teacherName(),
                    row.studentCode(),
                    row.studentName(),
                    row.status() == null ? null : row.status().name(),
                    row.remarks());
        }
        return new CsvDocument("attendance-date-" + date + ".csv", csv.toCsv());
    }

    /**
     * The percentage as a plain number ("91.7"); an undefined
     * percentage stays an empty field instead of becoming a fake 0.
     */
    private static String percentage(AttendanceReportRow row) {
        return row.percentage() == null ? "" : row.percentage().toPlainString();
    }

    private static String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    /**
     * A safe, descriptive file name part built from the selected range
     * only - no user-entered text ever reaches the file name.
     */
    private static String rangeLabel(LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return from + "-to-" + to;
        }
        if (from != null) {
            return "from-" + from;
        }
        if (to != null) {
            return "to-" + to;
        }
        return "all-dates";
    }
}
