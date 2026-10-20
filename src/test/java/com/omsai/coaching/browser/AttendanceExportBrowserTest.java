package com.omsai.coaching.browser;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.Media;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.student.Student;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-browser tests for the attendance CSV exports and the printable
 * attendance sheet: the download links an administrator sees, the
 * content of the downloaded file, the effect of the report filters, the
 * print rendering of the sheet and the permission walls around them.
 *
 * <p>The printed artifacts (screenshots and PDFs) are written to
 * {@code target/browser-artifacts}.</p>
 */
@Tag("browser")
class AttendanceExportBrowserTest extends BrowserTestBase {

    @Test
    void adminDownloadsTheBatchReportCsvWithTheExpectedHeadersAndRecords() throws Exception {
        var tree = seedBaseTree("ExportAdmin");
        var student = trackedStudent("Export Browser Student");
        var enrollment = trackedEnrollment(student, tree.batch());

        loginAdmin();

        // A completed class with one absence, so the report has numbers.
        String sessionUrl = createSession(tree.batchSubject().getId(), "Export Browser Session",
                today());
        gotoPage(sessionUrl + "/attendance");
        page.locator("select[name='status_" + enrollment.getId() + "']").selectOption("ABSENT");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save attendance")).click();
        gotoPage(sessionUrl);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Complete")).click();

        // The report page offers the download.
        gotoPage("/attendance/reports/batch?batchId=" + tree.batch().getId());
        assertTrue(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Download CSV"))
                        .first().isVisible(),
                "The batch report must offer a CSV download for an administrator");

        assertNoClientOrServerErrors();
        Download download = page.waitForDownload(() ->
                page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Download CSV")).click());
        assertOnlyTheDownloadWasAborted();
        assertTrue(download.suggestedFilename().startsWith("attendance-batch-"),
                "The download must use a safe, descriptive file name, got " + download.suggestedFilename());
        assertTrue(download.suggestedFilename().endsWith(".csv"),
                "The download must be a CSV file, got " + download.suggestedFilename());

        String csv = Files.readString(download.path(), StandardCharsets.UTF_8);
        List<String> records = records(csv);
        assertTrue(records.get(0).startsWith("Student code,Student name,Course,Batch,Subject,Academic year,"),
                "The downloaded CSV must start with the report header, got: " + records.get(0));
        String record = records.stream()
                .filter(line -> line.startsWith(student.getStudentCode() + ","))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No record for " + student.getStudentCode()));
        // eligible=1, present=0, absent=1, late=0, excused=0, percentage=0.0
        assertTrue(record.endsWith("1,0,1,0,0,0.0"),
                "The downloaded record must carry the report numbers, got: " + record);
        assertTrue(record.contains(student.getFullName()), "The record must name the student");
        assertTrue(record.contains(tree.batch().getName()), "The record must name the batch");
        assertTrue(record.contains(tree.course().getName()), "The record must name the course");
    }

    @Test
    void theReportFilterIsReflectedInTheDownloadedCsv() throws Exception {
        var tree = seedBaseTree("ExportFilter");
        var student = trackedStudent("Export Filter Student");
        var enrollment = trackedEnrollment(student, tree.batch());

        loginAdmin();

        LocalDate first = today().minusDays(3);
        LocalDate second = today();
        String firstUrl = createSession(tree.batchSubject().getId(), "Export Filter First", first);
        gotoPage(firstUrl + "/attendance");
        page.locator("select[name='status_" + enrollment.getId() + "']").selectOption("PRESENT");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save attendance")).click();
        gotoPage(firstUrl);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Complete")).click();

        String secondUrl = createSession(tree.batchSubject().getId(), "Export Filter Second", second);
        gotoPage(secondUrl + "/attendance");
        page.locator("select[name='status_" + enrollment.getId() + "']").selectOption("ABSENT");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save attendance")).click();
        gotoPage(secondUrl);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Complete")).click();

        // Filtered to the first class only: the download must not contain
        // the second class.
        gotoPage("/attendance/reports/batch?batchId=" + tree.batch().getId()
                + "&from=" + first + "&to=" + first);
        assertNoClientOrServerErrors();
        Download download = page.waitForDownload(() ->
                page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Download CSV")).click());
        assertOnlyTheDownloadWasAborted();
        assertTrue(download.suggestedFilename().equals("attendance-batch-" + first + "-to-" + first + ".csv"),
                "The file name must reflect the selected range, got " + download.suggestedFilename());

        String csv = Files.readString(download.path(), StandardCharsets.UTF_8);
        String record = records(csv).stream()
                .filter(line -> line.startsWith(student.getStudentCode() + ","))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No record for " + student.getStudentCode()));
        // Only the first class is inside the range: eligible=1, present=1.
        assertTrue(record.endsWith("1,1,0,0,0,100.0"),
                "The download must contain only the filtered range, got: " + record);
    }

    @Test
    void thePrintableSheetRendersAndPrintModeHidesTheControls() throws Exception {
        var tree = seedBaseTree("SheetAdmin");
        var student = trackedStudent("Sheet Browser Student");
        var enrollment = trackedEnrollment(student, tree.batch());
        var teacherUser = trackedTeacherUser("sheet.browser.teacher");
        var teacherProfile = trackedTeacherProfile(teacherUser, "Sheet Browser Teacher");
        fixtures.assignTeacher(tree.batchSubject(), teacherProfile);

        loginAdmin();

        String sessionUrl = createSession(tree.batchSubject().getId(), "Sheet Browser Session", today());
        gotoPage(sessionUrl + "/attendance");
        page.locator("select[name='status_" + enrollment.getId() + "']").selectOption("LATE");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save attendance")).click();

        // The session page offers the printable sheet.
        gotoPage(sessionUrl);
        assertTrue(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Print attendance sheet"))
                        .first().isVisible(),
                "The session page must offer the printable attendance sheet");

        // The sheet itself.
        gotoPage(sessionUrl + "/print");
        assertTrue(page.getByText("Om Sai Coaching").first().isVisible(),
                "The sheet must carry the institute heading");
        assertTrue(page.getByText(student.getFullName()).first().isVisible(),
                "The sheet must list the student of the roster");
        assertTrue(page.getByText(student.getStudentCode()).first().isVisible(),
                "The sheet must list the student code");
        assertTrue(page.getByText(tree.course().getName()).first().isVisible(),
                "The sheet must name the course");
        assertTrue(page.getByText(tree.batch().getName()).first().isVisible(),
                "The sheet must name the batch");
        assertTrue(page.getByText(tree.subject().getName()).first().isVisible(),
                "The sheet must name the subject");
        assertTrue(page.getByText(teacherProfile.getFullName()).first().isVisible(),
                "The sheet must name the assigned teacher");
        // The recorded mark is visible on the sheet.
        assertEquals(1, page.locator("td.mark-box.marked").count(),
                "The recorded attendance must be marked on the sheet");
        assertEquals("LATE", page.locator("td.mark-box.marked .mark").getAttribute("title"),
                "The marked box must be the recorded status");

        // Screen rendering.
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(ARTIFACTS_DIR.resolve("attendance-sheet-screen.png")
                        .toAbsolutePath().normalize())
                .setFullPage(true));

        // Print rendering: the controls disappear, the roster stays.
        page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
        assertEquals("none",
                page.locator(".no-print").first().evaluate("el => getComputedStyle(el).display"),
                "Print media must hide the sheet controls");
        assertEquals(0, page.locator(".app-sidebar").count(),
                "The printable sheet carries no navigation");
        assertTrue(page.locator(".sheet-table tbody tr").count() >= 1,
                "The roster must survive the print rendering");
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(ARTIFACTS_DIR.resolve("attendance-sheet-print.png")
                        .toAbsolutePath().normalize())
                .setFullPage(true));

        // The browser's own print pipeline produces the PDF.
        Path pdf = ARTIFACTS_DIR.resolve("attendance-sheet-printed.pdf").toAbsolutePath().normalize();
        page.pdf(new Page.PdfOptions().setPath(pdf));
        String text = pdfText(pdf.toString());
        // Narrow table cells are wrapped by the print layout, so the
        // extracted text is compared without white space.
        String printed = squash(text);
        assertTrue(printed.contains(squash("Om Sai Coaching")),
                "The printed sheet must contain the heading, got: " + text);
        assertTrue(printed.contains(squash(student.getFullName())),
                "The printed sheet must contain the student name, got: " + text);
        assertTrue(printed.contains(squash(student.getStudentCode())),
                "The printed sheet must contain the student code, got: " + text);
        assertTrue(printed.contains(squash(tree.batch().getName())),
                "The printed sheet must contain the batch name, got: " + text);
        assertTrue(!printed.contains("DownloadCSV"), "The printed sheet must not contain interface text");

        assertNoClientOrServerErrors();
    }

    @Test
    void teachersCannotExportReportsAndOnlySeeTheirOwnSheets() throws Exception {
        var tree = seedBaseTree("SheetGuard");
        var student = trackedStudent("Sheet Guard Student");
        trackedEnrollment(student, tree.batch());

        // Another teacher's assignment in the same batch (a second subject,
        // because (batch, course_subject) is unique).
        var otherSubject = fixtures.subject("Sheet Guard Other Subject");
        var otherCourseSubject = fixtures.assign(tree.course(), otherSubject);
        trackedSubjectIds.add(otherSubject.getId());
        trackedCourseSubjectIds.add(otherCourseSubject.getId());
        var otherBatchSubject = trackedBatchSubject(tree.batch(), otherCourseSubject);

        var ownerUser = trackedTeacherUser("sheet.guard.owner");
        var ownerProfile = trackedTeacherProfile(ownerUser, "Sheet Guard Owner");
        fixtures.assignTeacher(tree.batchSubject(), ownerProfile);
        var outsiderUser = trackedTeacherUser("sheet.guard.outsider");
        trackedTeacherProfile(outsiderUser, "Sheet Guard Outsider");

        loginAdmin();
        String ownSessionUrl = createSession(tree.batchSubject().getId(), "Sheet Guard Own", today());
        String otherSessionUrl = createSession(otherBatchSubject.getId(), "Sheet Guard Other", today());

        // The teacher may not reach the reports or their downloads.
        logout();
        loginTeacher(ownerUser);

        assertForbidden("/attendance/reports/export/batch?batchId=" + tree.batch().getId(),
                "the batch report download");
        assertForbidden("/attendance/reports/export/date?date=" + today(),
                "the date report download");
        assertForbidden("/attendance/reports/export/student/" + student.getId(),
                "the student report download");
        assertEquals(0, page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Download CSV")).count(),
                "A teacher must not see a download link");

        // Their own sheet is allowed, the other teacher's is not.
        gotoPage(ownSessionUrl + "/print");
        assertTrue(page.getByText("Om Sai Coaching").first().isVisible(),
                "The assigned teacher must be able to print their own session sheet");
        assertForbidden(otherSessionUrl + "/print", "another teacher's session sheet");

        assertNoClientOrServerErrors(403);
    }

    /* ----- helpers ---------------------------------------------------------- */

    private String createSession(long batchSubjectId, String topic, LocalDate date) {
        gotoPage("/sessions/new");
        page.locator("#batchSubjectId").selectOption(String.valueOf(batchSubjectId));
        page.locator("#sessionDate").fill(date.toString());
        page.locator("#startTime").fill("09:00");
        page.locator("#endTime").fill("10:00");
        page.locator("#topic").fill(topic);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create session")).click();
        assertFlash("Class session created.");
        assertTrue(page.url().matches(".*/sessions/\\d+"),
                "Expected the session detail page, got " + page.url());
        return page.url();
    }

    /**
     * Chromium reports the navigation that starts a download as an
     * aborted request - that is the only failure the download may
     * cause, and the server answered it with a real 200.
     */
    private void assertOnlyTheDownloadWasAborted() {
        List<String> unexpected = requestFailures.stream()
                .filter(line -> !line.contains("/attendance/reports/export/"))
                .toList();
        assertTrue(unexpected.isEmpty(), "Only the download navigation may be aborted, got: " + unexpected);
        assertTrue(requestFailures.stream().allMatch(line -> line.contains("ERR_ABORTED")),
                "The download navigation must be the aborted one, got: " + requestFailures);
        assertTrue(serverErrors.isEmpty(), "The download must not produce a server error: " + serverErrors);
    }

    private void assertForbidden(String path, String description) {
        gotoPage(path);
        assertTrue(page.getByText("You do not have access to that feature").first().isVisible(),
                "Teachers must be denied " + description + " (" + page.url() + ")");
    }

    /** Splits a CSV document into logical records; a quoted field may contain line breaks. */
    private static List<String> records(String csv) {
        List<String> records = new java.util.ArrayList<>();
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

    /** The text without any white space, to compare wrapped PDF lines. */
    private static String squash(String text) {
        return text.replaceAll("\\s+", "").toLowerCase();
    }

    /** The text of a generated PDF. */
    private static String pdfText(String path) throws Exception {
        try (PDDocument document = Loader.loadPDF(new File(path))) {
            return new PDFTextStripper().getText(document);
        }
    }
}
