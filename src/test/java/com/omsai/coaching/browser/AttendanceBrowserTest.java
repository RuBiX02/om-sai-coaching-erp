package com.omsai.coaching.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.omsai.coaching.security.UserAccount;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-browser tests for the Phase 2 class-session and attendance
 * module: creating a session through the form, marking attendance,
 * completion, the attendance reports, and the permission walls around
 * teacher ownership and completed/cancelled sessions.
 */
@Tag("browser")
class AttendanceBrowserTest extends BrowserTestBase {

    private static final String SESSION_TOPIC = "Attendance browser topic";

    @Test
    void adminCreatesSessionMarksAttendanceCompletesAndReadsTheReports() {
        var tree = seedBaseTree("AttenAdmin");
        var student = trackedStudent("Attendance Browser Student");
        var enrollment = trackedEnrollment(student, tree.batch());
        var teacherUser = trackedTeacherUser("atten.admin.teacher");
        var teacherProfile = trackedTeacherProfile(teacherUser, "Attendance Admin Teacher");
        fixtures.assignTeacher(tree.batchSubject(), teacherProfile);

        loginAdmin();

        // --- create the session through the rendered form -----------------
        gotoPage("/sessions/new");
        page.locator("#batchSubjectId")
                .selectOption(String.valueOf(tree.batchSubject().getId()));
        page.locator("#sessionDate").fill(today().toString());
        page.locator("#startTime").fill("09:00");
        page.locator("#endTime").fill("10:00");
        page.locator("#topic").fill(SESSION_TOPIC);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create session")).click();

        assertFlash("Class session created.");
        assertTrue(page.url().matches(".*/sessions/\\d+"),
                "After creation the browser must land on the session detail page, got " + page.url());
        assertTrue(page.getByText(SESSION_TOPIC).first().isVisible(),
                "The session detail page must show the topic");
        assertTrue(page.getByText("Open", new Page.GetByTextOptions().setExact(true)).first().isVisible(),                "A new session starts as Open");
        String sessionUrl = page.url();
        long sessionId = Long.parseLong(sessionUrl.replaceAll(".*?/sessions/(\\d+).*", "$1"));

        // --- mark attendance through the roster form -----------------------
        gotoPage("/sessions/" + sessionId + "/attendance");
        assertTrue(page.getByText("Roster on the class date").first().isVisible(),
                "The attendance roster must be visible for an open session");
        page.locator("select[name='status_" + enrollment.getId() + "']").selectOption("ABSENT");
        page.locator("input[name='remark_" + enrollment.getId() + "']")
                .fill("Marked by the browser test");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save attendance")).click();
        assertFlash("Attendance saved for 1 student(s).");
        assertTrue(page.getByText("ABSENT", new Page.GetByTextOptions().setExact(true)).first().isVisible(),
                "The session detail must show the recorded ABSENT mark");
        assertTrue(page.getByText("Marked by the browser test").first().isVisible(),
                "The session detail must show the recorded remark");

        // --- complete the session (confirm dialog auto-accepted) -----------
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Complete")).click();
        assertFlash("Session completed. Teachers may no longer change its attendance;"
                + " administrators can still correct it.");
        assertTrue(page.getByText("Completed", new Page.GetByTextOptions().setExact(true)).first().isVisible(),
                "The session badge must read Completed");

        // --- attendance report for the student ------------------------------
        gotoPage("/attendance/reports/student/" + student.getId());
        assertTrue(page.getByText(student.getFullName()).first().isVisible(),
                "The student report page must show the student");
        assertTrue(page.getByText("0%").first().isVisible(),
                "One absent session out of one held session must report 0%");
        assertFalse(page.getByText("Insufficient data").first().isVisible(),
                "With a completed session in the enrollment period the report must have data");
        // The report's row layout: headings (course/batch/period) and count
        // columns — the student name itself lives in the heading card above.
        String table = page.locator("table").last().innerText().replaceAll("\\s+", " ");
        assertTrue(table.contains("1 0 1 0 0 0.0%"), "The report row must read eligible=1, present=0, "
                + "absent=1, late=0, excused=0, percentage=0.0% — got: " + table);
        assertNoClientOrServerErrors();
    }

    @Test
    void teacherWorksOnlyOnOwnSessionsAndIsBlockedFromFeeFeatures() {
        var tree = seedBaseTree("AttenTeacher");
        trackedEnrollment(trackedStudent("Other Roster Student"), tree.batch());
        var teacherOne = trackedTeacherUser("atten.own.teacher");
        var teacherProfileOne = trackedTeacherProfile(teacherOne, "Teacher One Own");
        fixtures.assignTeacher(tree.batchSubject(), teacherProfileOne);

        // A second batch-subject assignment for another teacher. The
        // (batch, course-subject) pairs are unique, so the second teacher
        // needs their own subject assigned to the same batch.
        var otherSubject = fixtures.subject("AttenTeacher Other Subject");
        var otherCourseSubject = fixtures.assign(tree.course(), otherSubject);
        trackedSubjectIds.add(otherSubject.getId());
        trackedCourseSubjectIds.add(otherCourseSubject.getId());
        var otherBatchSubject = trackedBatchSubject(tree.batch(), otherCourseSubject);
        var teacherTwo = trackedTeacherUser("atten.other.teacher");
        var teacherProfileTwo = trackedTeacherProfile(teacherTwo, "Teacher Two Other");
        fixtures.assignTeacher(otherBatchSubject, teacherProfileTwo);

        loginAdmin();
        String ownSessionUrl = createSession(tree.batchSubject().getId(), "Teacher One Own Session");
        String otherSessionUrl = createSession(otherBatchSubject.getId(), "Teacher Two Other Session");

        // Switch to the first teacher.
        logout();
        loginTeacher(teacherOne);

        // Admin-only navigation must be absent from the teacher sidebar.
        for (String adminOnly : List.of("Attendance reports", "Fees", "Payments", "Accounts")) {
            assertEquals(0, page.locator(".app-sidebar .nav-link",
                            new com.microsoft.playwright.Page.LocatorOptions().setHasText(adminOnly)).count(),
                    "Teacher sidebar must not offer the '" + adminOnly + "' link");
        }

        // Only the own session is listed (rows are keyed by the teacher name).
        gotoPage("/sessions");
        assertTrue(page.getByText(teacherProfileOne.getFullName()).first().isVisible(),
                "The teacher's own session (their assignment row) must be listed on /sessions");
        assertEquals(0, page.getByText(teacherProfileTwo.getFullName()).count(),
                "Sessions of other teachers must not be listed for this teacher");

        // Own session: attendance workflow is available while OPEN.
        gotoPage(ownSessionUrl);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Record attendance")).click();
        assertTrue(page.getByText("Roster on the class date").first().isVisible(),
                "The teacher must be able to work on the attendance of their own open session");
        assertTrue(page.locator("select[name^='status_']").count() >= 1,
                "The roster must be editable for the owning teacher");

        // Another teacher's session → access denied on detail and attendance.
        gotoPage(otherSessionUrl);
        assertTrue(page.getByText("You do not have access to that feature").first().isVisible(),
                "Opening another teacher's session must show the access-denied page");
        assertForbidden(otherSessionUrl + "/attendance", "the attendance of another teacher's session");

        // Fee/payment/report features are administrator-only.
        assertForbidden("/fees", "the fee agreement list");
        assertForbidden("/payments", "the payments list");
        assertForbidden("/attendance/reports", "the attendance reports");
        assertNoClientOrServerErrors(403);
    }

    @Test
    void completedSessionsLockAttendanceForTeachersAndCancelledSessionsRefuseIt() {
        var tree = seedBaseTree("AttenLock");
        var student = trackedStudent("Locked Attendance Student");
        var enrollment = trackedEnrollment(student, tree.batch());
        var teacherUser = trackedTeacherUser("atten.lock.teacher");
        var teacherProfile = trackedTeacherProfile(teacherUser, "Locked Teacher");
        fixtures.assignTeacher(tree.batchSubject(), teacherProfile);

        loginAdmin();

        // Session A: admin marks attendance and completes it.
        String completedSessionUrl = createSession(tree.batchSubject().getId(), "Locked Completed Session");
        gotoPage(completedSessionUrl + "/attendance");
        page.locator("select[name='status_" + enrollment.getId() + "']").selectOption("ABSENT");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save attendance")).click();
        gotoPage(completedSessionUrl);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Complete")).click();
        assertFlash("Session completed. Teachers may no longer change its attendance;"
                + " administrators can still correct it.");

        // Teacher view of a completed session: read-only with a warning.
        logout();
        loginTeacher(teacherUser);
        gotoPage(completedSessionUrl + "/attendance");
        assertTrue(page.getByText(
                        "This session is completed. Attendance can only be corrected by an administrator.")
                        .first().isVisible(),
                "Teachers must see the completed-session warning");
        assertEquals(0, page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Save attendance")).count(),
                "Teachers must not be able to edit attendance of a completed session");
        assertEquals(0, page.locator("select[name^='status_']").count(),
                "The roster selects must not render for a restricted completed session");

        // Back to admin: session B is cancelled and must refuse attendance.
        logout();
        loginAdmin();
        String cancelledSessionUrl = createSession(tree.batchSubject().getId(), "Locked Cancelled Session");
        gotoPage(cancelledSessionUrl);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancel session")).click();
        assertFlash("Session cancelled. Its record is preserved.");

        gotoPage(cancelledSessionUrl + "/attendance");
        assertTrue(page.getByText(
                        "This session was cancelled. Attendance records must not exist for a cancelled class.")
                        .first().isVisible(),
                "A cancelled session must show its refusal message");
        assertEquals(0, page.locator("select[name^='status_']").count(),
                "A cancelled session must not render the roster form");
        assertEquals(0, page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Save attendance")).count(),
                "A cancelled session must not offer a save action");
        assertNoClientOrServerErrors();
    }

    /* ----- helpers ---------------------------------------------------------- */

    private String createSession(long batchSubjectId, String topic) {
        gotoPage("/sessions/new");
        page.locator("#batchSubjectId").selectOption(String.valueOf(batchSubjectId));
        page.locator("#sessionDate").fill(today().toString());
        page.locator("#startTime").fill("09:00");
        page.locator("#endTime").fill("10:00");
        page.locator("#topic").fill(topic);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create session")).click();
        assertFlash("Class session created.");
        assertTrue(page.url().matches(".*/sessions/\\d+"),
                "Expected the session detail page, got " + page.url());
        return page.url();
    }

    private void assertForbidden(String path, String description) {
        gotoPage(path);
        assertTrue(page.getByText("You do not have access to that feature").first().isVisible(),
                "Teachers must be denied " + description + " (" + page.url() + ")");
        assertTrue(page.url().contains(path),
                "The access-denied page must be served at the denied URL itself");
    }
}
