package com.omsai.coaching.attendance;

import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.batch.CourseSubjectRepository;
import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.student.StudentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * Administrator-only attendance reporting screens. All values are
 * computed from the database; percentages follow the documented
 * formula and cancelled/open sessions never enter any denominator.
 */
@Controller
@RequestMapping("/attendance/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AttendanceReportController {

    private final AttendanceReportService reportService;
    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final BatchSubjectRepository batchSubjectRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final BusinessClock businessClock;

    public AttendanceReportController(AttendanceReportService reportService,
                                      StudentRepository studentRepository,
                                      BatchRepository batchRepository,
                                      BatchSubjectRepository batchSubjectRepository,
                                      CourseSubjectRepository courseSubjectRepository,
                                      BusinessClock businessClock) {
        this.reportService = reportService;
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.batchSubjectRepository = batchSubjectRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.businessClock = businessClock;
    }

    /** Report hub with links, the student search and the date report form. */
    @GetMapping
    public String hub(@RequestParam(required = false) String q, Model model) {
        if (q != null && !q.isBlank()) {
            String term = q.trim().toLowerCase();
            model.addAttribute("students",
                    studentRepository.search(term, null, PageRequest.of(0, 10, Sort.by("fullName"))));
        }
        model.addAttribute("q", q);
        model.addAttribute("defaultDate", businessClock.today());
        return "attendance/reports";
    }

    /** Individual student attendance report with optional date range. */
    @GetMapping("/student/{studentId}")
    public String student(@PathVariable Long studentId,
                          @RequestParam(required = false) LocalDate from,
                          @RequestParam(required = false) LocalDate to,
                          Model model) {
        try {
            AttendanceReportService.StudentReport report = reportService.forStudent(studentId, from, to);
            long[] totals = AttendanceReportRow.aggregate(report.rows());
            model.addAttribute("report", report);
            model.addAttribute("totals", totals);
            model.addAttribute("overallPercentage",
                    AttendanceService.percentage(totals[0], totals[1], totals[2]));
        } catch (BusinessRuleException e) {
            model.addAttribute("errorMessage", e.getMessage());
        }
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "attendance/report-student";
    }

    /** Batch report: students of one batch over a date range. */
    @GetMapping("/batch")
    public String batch(@RequestParam(required = false) Long batchId,
                        @RequestParam(required = false) LocalDate from,
                        @RequestParam(required = false) LocalDate to,
                        Model model) {
        model.addAttribute("batches", batchRepository.findAllForSelection());
        model.addAttribute("batchId", batchId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        if (batchId == null) {
            return "attendance/report-batch";
        }
        if (!batchRepository.existsById(batchId)) {
            model.addAttribute("errorMessage", "Batch not found.");
            return "attendance/report-batch";
        }
        List<AttendanceReportRow> rows = reportService.forBatch(batchId, from, to);
        model.addAttribute("rows", rows);
        long[] totals = AttendanceReportRow.aggregate(rows);
        model.addAttribute("totals", totals);
        model.addAttribute("overallPercentage", AttendanceService.percentage(totals[0], totals[1], totals[2]));
        return "attendance/report-batch";
    }

    /** Subject report: one subject within a specific batch. */
    @GetMapping("/subject")
    public String subject(@RequestParam(required = false) Long batchId,
                          @RequestParam(required = false) Long courseSubjectId,
                          @RequestParam(required = false) LocalDate from,
                          @RequestParam(required = false) LocalDate to,
                          Model model) {
        model.addAttribute("batches", batchRepository.findAllForSelection());
        model.addAttribute("courseSubjects", courseSubjectRepository.findAllWithSubjectSorted());
        model.addAttribute("batchId", batchId);
        model.addAttribute("courseSubjectId", courseSubjectId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        if (batchId == null || courseSubjectId == null) {
            return "attendance/report-subject";
        }
        var batchSubject = batchSubjectRepository
                .findByBatchIdAndCourseSubjectId(batchId, courseSubjectId)
                .orElse(null);
        if (batchSubject == null) {
            model.addAttribute("errorMessage", "This subject is not assigned to that batch.");
            return "attendance/report-subject";
        }
        model.addAttribute("assignment", batchSubject);
        List<AttendanceReportRow> rows = reportService.forBatchSubject(batchSubject.getId(), from, to);
        model.addAttribute("rows", rows);
        long[] totals = AttendanceReportRow.aggregate(rows);
        model.addAttribute("totals", totals);
        model.addAttribute("overallPercentage", AttendanceService.percentage(totals[0], totals[1], totals[2]));
        return "attendance/report-subject";
    }

    /** Date report: all sessions of one date. */
    @GetMapping("/date")
    public String date(@RequestParam(required = false) LocalDate date, Model model) {
        model.addAttribute("date", date);
        if (date == null) {
            return "attendance/report-date";
        }
        model.addAttribute("report", reportService.forDate(date));
        return "attendance/report-date";
    }

    /** Small template helper for readable status badges. */
    public static String badge(Attendance.Status status) {
        return switch (status) {
            case PRESENT -> "text-bg-success";
            case EXCUSED -> "text-bg-info";
            case LATE -> "text-bg-warning";
            case ABSENT -> "text-bg-danger";
        };
    }
}
