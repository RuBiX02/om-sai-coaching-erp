package com.omsai.coaching.attendance;

import com.omsai.coaching.common.BusinessRuleException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * CSV downloads of the attendance reports.
 *
 * <p>The whole controller is administrator-only, exactly like the
 * report pages it mirrors: a teacher can neither open a report page
 * nor download its CSV, and the filters of the page are the filters of
 * the download. The attendance numbers are produced by
 * {@link AttendanceExportService}, which reuses the report service -
 * this controller only shapes the response.</p>
 */
@Controller
@RequestMapping("/attendance/reports/export")
@PreAuthorize("hasRole('ADMIN')")
public class AttendanceExportController {

    private final AttendanceExportService exportService;

    public AttendanceExportController(AttendanceExportService exportService) {
        this.exportService = exportService;
    }

    /** The individual student report as CSV. */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<byte[]> student(@PathVariable Long studentId,
                                          @RequestParam(required = false) LocalDate from,
                                          @RequestParam(required = false) LocalDate to) {
        return download(() -> exportService.studentReport(studentId, from, to));
    }

    /** The batch report as CSV. */
    @GetMapping("/batch")
    public ResponseEntity<byte[]> batch(@RequestParam(required = false) Long batchId,
                                        @RequestParam(required = false) LocalDate from,
                                        @RequestParam(required = false) LocalDate to) {
        if (batchId == null) {
            return missingFilter("Choose a batch before downloading the report.");
        }
        return download(() -> exportService.batchReport(batchId, from, to));
    }

    /** The subject report as CSV. */
    @GetMapping("/subject")
    public ResponseEntity<byte[]> subject(@RequestParam(required = false) Long batchId,
                                          @RequestParam(required = false) Long courseSubjectId,
                                          @RequestParam(required = false) LocalDate from,
                                          @RequestParam(required = false) LocalDate to) {
        if (batchId == null || courseSubjectId == null) {
            return missingFilter("Choose a batch and a subject before downloading the report.");
        }
        return download(() -> exportService.subjectReport(batchId, courseSubjectId, from, to));
    }

    /** The date report as CSV. */
    @GetMapping("/date")
    public ResponseEntity<byte[]> date(@RequestParam(required = false) LocalDate date) {
        if (date == null) {
            return missingFilter("Choose a date before downloading the report.");
        }
        return download(() -> exportService.dateReport(date));
    }

    // ----- helpers -----------------------------------------------------------

    /** Builds the download; business rule problems become a 404. */
    private ResponseEntity<byte[]> download(ExportCall call) {
        AttendanceExportService.CsvDocument document;
        try {
            document = call.export();
        } catch (BusinessRuleException e) {
            return notFound(e.getMessage());
        }
        byte[] body = document.content().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + document.filename() + "\"")
                .contentLength(body.length)
                .body(body);
    }

    private ResponseEntity<byte[]> missingFilter(String message) {
        return ResponseEntity.badRequest()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(message.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<byte[]> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(message.getBytes(StandardCharsets.UTF_8));
    }

    /** Small functional interface so the filters are checked before the export runs. */
    @FunctionalInterface
    private interface ExportCall {
        AttendanceExportService.CsvDocument export();
    }
}
