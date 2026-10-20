package com.omsai.coaching.web;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.attendance.Attendance;
import com.omsai.coaching.attendance.AttendanceForm;
import com.omsai.coaching.attendance.AttendanceService;
import com.omsai.coaching.attendance.ClassSession;
import com.omsai.coaching.attendance.ClassSessionService;
import com.omsai.coaching.attendance.SessionForm;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.support.Fixtures;
import com.omsai.coaching.teacher.Teacher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CSV downloads of the attendance reports.
 *
 * <p>Verified here: the files are real downloads with a header row and
 * a safe file name, the filters of the page are the filters of the
 * download, the numbers are the ones the report service produces, an
 * undefined percentage stays empty instead of becoming a fake 0, and
 * teachers (like anonymous visitors) cannot obtain a report at all.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttendanceExportWebTest {

    private static final LocalDate DAY_ONE = LocalDate.of(2026, 9, 10);
    private static final LocalDate DAY_TWO = LocalDate.of(2026, 9, 20);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private ClassSessionService sessionService;

    @Autowired
    private AttendanceService attendanceService;

    private Course course;
    private Batch batch;
    private BatchSubject assignment;
    private UserAccount admin;

    private Enrollment presentStudent;
    private Enrollment lateStudent;
    private Enrollment absentStudent;
    private Enrollment excusedStudent;
    private Enrollment commaStudent;
    private Enrollment quoteStudent;
    private Enrollment formulaStudent;
    private Enrollment lateJoiner;

    @BeforeEach
    void buildFixture() {
        course = fixtures.course("Export Course");
        batch = fixtures.batch(course, "Morning");
        assignment = fixtures.assignCourseSubject(batch,
                fixtures.assign(course, fixtures.subject("Export Subject")));
        Teacher teacher = fixtures.teacher("Export Teacher");
        fixtures.assignTeacher(assignment, teacher);
        admin = fixtures.adminUser("exporter");

        presentStudent = fixtures.activeEnrollment(fixtures.student("Present Student"), batch, DAY_ONE.minusDays(9));
        lateStudent = fixtures.activeEnrollment(fixtures.student("Late Student"), batch, DAY_ONE.minusDays(9));
        absentStudent = fixtures.activeEnrollment(fixtures.student("Absent Student"), batch, DAY_ONE.minusDays(9));
        excusedStudent = fixtures.activeEnrollment(fixtures.student("Excused Student"), batch, DAY_ONE.minusDays(9));
        commaStudent = fixtures.activeEnrollment(fixtures.student("Comma, Student"), batch, DAY_ONE.minusDays(9));
        quoteStudent = fixtures.activeEnrollment(fixtures.student("Quote \"Student\""), batch, DAY_ONE.minusDays(9));
        formulaStudent = fixtures.activeEnrollment(fixtures.student("=1+1"), batch, DAY_ONE.minusDays(9));
        // Enrolled after the first class: never on that roster.
        lateJoiner = fixtures.activeEnrollment(fixtures.student("Late Joiner"), batch, DAY_TWO.minusDays(5));

        ClassSession first = session(DAY_ONE);
        mark(first, presentStudent, Attendance.Status.PRESENT, null);
        mark(first, lateStudent, Attendance.Status.LATE, "arrived late");
        mark(first, absentStudent, Attendance.Status.ABSENT, null);
        mark(first, excusedStudent, Attendance.Status.EXCUSED, null);
        mark(first, commaStudent, Attendance.Status.PRESENT, null);
        mark(first, quoteStudent, Attendance.Status.PRESENT, "first line\nsecond line");
        mark(first, formulaStudent, Attendance.Status.PRESENT, null);
        sessionService.complete(first.getId(), admin);

        ClassSession second = session(DAY_TWO);
        mark(second, presentStudent, Attendance.Status.PRESENT, null);
        mark(second, lateJoiner, Attendance.Status.PRESENT, null);
        sessionService.complete(second.getId(), admin);

        // Left OPEN and CANCELLED on purpose: neither may enter a
        // denominator, and neither carries attendance.
        session(LocalDate.of(2026, 9, 25));
        ClassSession cancelled = session(LocalDate.of(2026, 9, 26));
        sessionService.cancel(cancelled.getId(), admin);
    }

    // ----- the four report types -------------------------------------------

    @Test
    void studentReportCsvHasAHeaderAndTheStudentsHistory() throws Exception {
        MvcResult result = download("/attendance/reports/export/student/" + presentStudent.getStudent().getId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String csv = body(result);
        List<String> lines = records(csv);

        assertThat(lines.get(0)).isEqualTo("Student code,Student name,Course,Batch,Enrolled from,Enrolled to,"
                + "Eligible completed sessions,Present,Absent,Late,Excused,Attendance percentage");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).contains(presentStudent.getStudent().getStudentCode());
        assertThat(lines.get(1)).contains("Present Student");
        assertThat(lines.get(1)).contains(course.getName());
        assertThat(lines.get(1)).contains(batch.getName());
        // two completed sessions, both present -> 100.0
        assertThat(lines.get(1)).endsWith("2,2,0,0,0,100.0");
    }

    @Test
    void batchReportCsvUsesTheSameNumbersAsTheReport() throws Exception {
        MvcResult result = download("/attendance/reports/export/batch?batchId=" + batch.getId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String csv = body(result);
        List<String> lines = records(csv);

        assertThat(lines.get(0)).startsWith("Student code,Student name,Course,Batch,Subject,Academic year,");
        String absentLine = lineOf(lines, absentStudent);
        // Two completed sessions on the roster, one absence:
        // (present + late) / (present + late + absent) = 0.0
        assertThat(absentLine).endsWith("2,0,1,0,0,0.0");
        String presentLine = lineOf(lines, presentStudent);
        assertThat(presentLine).endsWith("2,2,0,0,0,100.0");
        // The open and the cancelled session are not counted.
        assertThat(presentLine).contains("All subjects").contains("2026-27");
    }

    @Test
    void batchReportCsvHonoursTheSelectedDateRange() throws Exception {
        MvcResult result = download("/attendance/reports/export/batch?batchId=" + batch.getId()
                + "&from=2026-09-15&to=2026-09-30");

        String csv = body(result);
        List<String> lines = records(csv);
        // Only the second class is inside the range.
        assertThat(lineOf(lines, presentStudent)).endsWith("1,1,0,0,0,100.0");
        // The absent student was on the roster but has no mark in the
        // range: one eligible session, no evaluated attendance, so the
        // percentage is empty - never 0 and never 100.
        String absentLine = lineOf(lines, absentStudent);
        assertThat(absentLine).endsWith("1,0,0,0,0,");
        assertThat(absentLine).doesNotContain("0.0");
    }

    @Test
    void subjectReportCsvNamesTheSubjectAndTheBatch() throws Exception {
        MvcResult result = download("/attendance/reports/export/subject?batchId=" + batch.getId()
                + "&courseSubjectId=" + assignment.getCourseSubject().getId());

        String csv = body(result);
        List<String> lines = records(csv);
        assertThat(lines.get(0)).startsWith("Student code,Student name,Course,Batch,Subject,Academic year,");
        assertThat(lineOf(lines, presentStudent)).contains("Export Subject").contains(batch.getName());
    }

    @Test
    void dateReportCsvListsEveryRecordedMarkOfThatDate() throws Exception {
        MvcResult result = download("/attendance/reports/export/date?date=2026-09-10");

        String csv = body(result);
        List<String> lines = records(csv);
        assertThat(lines.get(0)).isEqualTo("Class date,Batch,Subject,Teacher,Student code,Student name,"
                + "Attendance status,Remarks");
        // Seven marks were recorded on that date; the second class is a
        // different date and does not appear.
        assertThat(lines).hasSize(8);
        assertThat(dateLineOf(lines, lateStudent)).contains("2026-09-10").contains("LATE").contains("arrived late");
        assertThat(dateLineOf(lines, presentStudent)).contains("PRESENT");
    }

    @Test
    void emptyReportStillProducesAValidCsvWithHeaders() throws Exception {
        Course emptyCourse = fixtures.course("Empty Course");
        Batch emptyBatch = fixtures.batch(emptyCourse, "Empty");

        MvcResult result = download("/attendance/reports/export/batch?batchId=" + emptyBatch.getId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String csv = body(result);
        assertThat(records(csv)).hasSize(1);
        assertThat(records(csv).get(0)).startsWith("Student code,Student name,Course,Batch,Subject,Academic year,");
    }

    // ----- data safety ------------------------------------------------------

    @Test
    void undefinedPercentageStaysEmptyAndIsNeverZero() throws Exception {
        MvcResult result = download("/attendance/reports/export/batch?batchId=" + batch.getId());

        String csv = body(result);
        List<String> lines = records(csv);
        // Only excused marks: the denominator is empty, so there is no
        // percentage at all.
        String excusedLine = lineOf(lines, excusedStudent);
        assertThat(excusedLine).endsWith("2,0,0,0,1,");
        assertThat(excusedLine).doesNotContain("0.0");
        assertThat(excusedLine).doesNotContain("100.0");
    }

    @Test
    void valuesWithCommasQuotesAndNewlinesAreEscaped() throws Exception {
        MvcResult result = download("/attendance/reports/export/date?date=2026-09-10");

        String csv = body(result);
        // A comma inside a name: the field is quoted.
        assertThat(csv).contains("\"" + commaStudent.getStudent().getFullName() + "\"");
        // A quote inside a name: the quote is doubled.
        assertThat(csv).contains("\"" + quoteStudent.getStudent().getFullName().replace("\"", "\"\"") + "\"");
        // A line break inside a remark: the field stays inside its quotes.
        assertThat(csv).contains("\"first line\nsecond line\"");
    }

    @Test
    void formulaLikeNamesAreNeutralised() throws Exception {
        MvcResult result = download("/attendance/reports/export/date?date=2026-09-10");

        String csv = body(result);
        assertThat(csv).contains("'" + formulaStudent.getStudent().getFullName());
        assertThat(csv).doesNotContain("\n=" + formulaStudent.getStudent().getFullName());
    }

    @Test
    void theDownloadUsesUtf8AndASafeFileName() throws Exception {
        MvcResult result = download("/attendance/reports/export/batch?batchId=" + batch.getId()
                + "&from=2026-09-15&to=2026-09-30");

        assertThat(result.getResponse().getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"attendance-batch-2026-09-15-to-2026-09-30.csv\"");
    }

    // ----- authorization ----------------------------------------------------

    @Test
    void teachersCannotDownloadAnyAttendanceReport() throws Exception {
        UserAccount teacher = fixtures.teacherUser("export.teacher");

        mockMvc.perform(get("/attendance/reports/export/student/1").with(as(teacher)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/attendance/reports/export/batch").param("batchId", "1").with(as(teacher)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/attendance/reports/export/subject")
                        .param("batchId", "1").param("courseSubjectId", "1").with(as(teacher)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/attendance/reports/export/date").param("date", "2026-09-10").with(as(teacher)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousVisitorsAreSentToTheLoginPage() throws Exception {
        mockMvc.perform(get("/attendance/reports/export/batch").param("batchId", "1"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void aMissingFilterIsAnsweredWithBadRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/attendance/reports/export/batch").with(as(admin)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);

        MvcResult unknownBatch = mockMvc.perform(
                get("/attendance/reports/export/batch").param("batchId", "999999").with(as(admin))).andReturn();
        assertThat(unknownBatch.getResponse().getStatus()).isEqualTo(404);
    }

    // ----- helpers ----------------------------------------------------------

    private ClassSession session(LocalDate date) {
        SessionForm form = new SessionForm();
        form.setBatchSubjectId(assignment.getId());
        form.setSessionDate(date);
        form.setStartTime(LocalTime.of(9, 0));
        return sessionService.create(form, admin);
    }

    private void mark(ClassSession session, Enrollment enrollment, Attendance.Status status, String remarks) {
        AttendanceForm form = new AttendanceForm();
        AttendanceForm.Row row = new AttendanceForm.Row();
        row.setEnrollmentId(enrollment.getId());
        row.setStatus(status);
        row.setRemarks(remarks);
        form.setRows(List.of(row));
        attendanceService.record(session.getId(), form, admin);
    }

    private MvcResult download(String url) throws Exception {
        return mockMvc.perform(get(url).with(as(admin))).andReturn();
    }

    private static RequestPostProcessor as(UserAccount account) {
        return user(AppUserPrincipal.from(account));
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Splits a CSV document into logical records; a quoted field may contain line breaks. */
    private static List<String> records(String csv) {
        List<String> records = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == '\n' && !inQuotes) {
                records.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            records.add(current.toString());
        }
        return records;
    }

    /** The record of one student in a report whose first column is the student code. */
    private static String lineOf(List<String> records, Enrollment enrollment) {
        return recordWith(records, enrollment.getStudent().getStudentCode(), 0);
    }

    /** The record of one student in the date report, whose student code is the fifth column. */
    private static String dateLineOf(List<String> records, Enrollment enrollment) {
        return recordWith(records, enrollment.getStudent().getStudentCode(), 4);
    }

    private static String recordWith(List<String> records, String studentCode, int column) {
        return records.stream()
                .filter(record -> fields(record).size() > column
                        && studentCode.equals(fields(record).get(column)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No CSV record for " + studentCode));
    }

    /** Splits one CSV record into its fields, honouring quoted values. */
    private static List<String> fields(String record) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < record.length(); i++) {
            char c = record.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < record.length() && record.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
