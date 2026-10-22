package com.omsai.coaching.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.student.Student;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The complete institute workflow in a real browser: a recurring
 * timetable is created, a double booking is refused, the classes are
 * generated, the teacher marks the attendance and enters the marks of
 * an examination, the administrator publishes the results - and the
 * student finally sees their own timetable, attendance, result and fee
 * information, and nothing of anybody else's.
 */
@Tag("browser")
class InstituteWorkflowBrowserTest extends BrowserTestBase {

    @Test
    void theWholeInstituteWorkflowWorksInTheBrowser() {
        var tree = seedBaseTree("Workflow");
        var student = trackedStudent("Workflow Student");
        var enrollment = trackedEnrollment(student, tree.batch());
        var teacherUser = trackedTeacherUser("workflow.teacher");
        var teacherProfile = trackedTeacherProfile(teacherUser, "Workflow Teacher");
        fixtures.assignTeacher(tree.batchSubject(), teacherProfile);

        loginAdmin();

        // A fee agreement, so the student sees their own financial view.
        var feeForm = new com.omsai.coaching.fees.FeeAccountForm();
        feeForm.setEnrollmentId(enrollment.getId());
        feeForm.setGrossFee(new java.math.BigDecimal("12000"));
        feeForm.setAcademicYear("2026-27");
        feeAccountService.create(feeForm, trackedAdminUser("workflow.fee.admin"));

        // --- 1. the recurring timetable -----------------------------------
        gotoPage("/timetable/new");
        page.locator("#batchSubjectId").selectOption(String.valueOf(tree.batchSubject().getId()));
        page.locator("#dayOfWeek").selectOption("MONDAY");
        page.locator("#startTime").fill("09:00");
        page.locator("#endTime").fill("10:00");
        page.locator("#classroom").fill("Room 1");
        page.locator("#effectiveFrom").fill(today().toString());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create timetable entry"))
                .click();
        assertFlash("Timetable entry created.");

        // The weekly timetable shows it.
        gotoPage("/timetable");
        assertTrue(page.getByText("Workflow Subject").first().isVisible(),
                "The weekly timetable must list the new class");
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(ARTIFACTS_DIR.resolve("timetable-weekly.png")
                        .toAbsolutePath().normalize())
                .setFullPage(true));
        assertTrue(page.getByText("Room 1").first().isVisible(),
                "The weekly timetable must show the classroom");

        // --- 2. a double booking is refused --------------------------------
        gotoPage("/timetable/new");
        page.locator("#batchSubjectId").selectOption(String.valueOf(tree.batchSubject().getId()));
        page.locator("#dayOfWeek").selectOption("MONDAY");
        page.locator("#startTime").fill("09:30");
        page.locator("#endTime").fill("10:30");
        page.locator("#classroom").fill("Room 2");
        page.locator("#effectiveFrom").fill(today().toString());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create timetable entry"))
                .click();
        assertTrue(page.getByText("already has a class on MONDAY").first().isVisible(),
                "The overlapping class must be refused with an explanation");

        // --- 3. the classes are generated ----------------------------------
        gotoPage("/timetable/generate");
        page.locator("#from").fill(today().toString());
        page.locator("#to").fill(today().plusDays(6).toString());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Generate classes")).click();
        assertTrue(page.getByText("Generated 1 class session(s)").first().isVisible(),
                "The generation must report the created class");

        // Running it again with the same range must not duplicate the class.
        page.locator("#from").fill(today().toString());
        page.locator("#to").fill(today().plusDays(6).toString());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Generate classes")).click();
        assertTrue(page.getByText("already existed").first().isVisible(),
                "The second run must not create a duplicate");

        // --- 4. the teacher marks the attendance ----------------------------
        logout();
        loginTeacher(teacherUser);
        Long sessionId = jdbc.queryForObject(
                "select id from class_sessions where batch_subject_id = ? order by id desc",
                Long.class, tree.batchSubject().getId());
        gotoPage("/sessions/" + sessionId);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Record attendance"))
                .first().click();
        page.locator("select[name^='status_']").first().selectOption("PRESENT");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save attendance")).click();
        assertFlash("Attendance saved for 1 student(s).");

        // The class is completed, so it counts in the attendance report.
        gotoPage("/sessions/" + sessionId);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Complete")).click();
        assertFlash("Session completed.");

        // --- 5. the examination and its marks -------------------------------
        gotoPage("/examinations/new");
        page.locator("#batchSubjectId").selectOption(String.valueOf(tree.batchSubject().getId()));
        page.locator("#title").fill("Workflow test");
        page.locator("#examinationDate").fill(today().toString());
        page.locator("#maximumMarks").fill("100");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create draft")).click();
        assertFlash("Examination created as a draft.");

        gotoPage("/examinations");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("View examination")).first().click();
        String examinationUrl = page.url();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Enter marks")).click();
        page.locator("select[name^='status_']").first().selectOption("PRESENT");
        page.locator("input[name^='marks_']").first().fill("80");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save marks")).click();
        assertFlash("Saved 1 result(s).");

        // --- 6. the administrator publishes the results ----------------------
        logout();
        loginAdmin();
        gotoPage(examinationUrl);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Publish results")).click();
        assertTrue(page.getByText("Examination published").first().isVisible(),
                "The publication must be reported");

        // --- 7. the student sees their own records --------------------------
        UserAccount studentAccount = trackedStudentAccount(student, "workflow.student");
        logout();
        login(studentAccount.getEmail(), fixtures.safePassword());
        assertTrue(page.url().endsWith("/student"),
                "A student lands on the student portal, got " + page.url());

        // The dashboard shows the attendance and the result.
        assertTrue(page.getByText("100.0%").first().isVisible(),
                "The student must see their own attendance percentage");
        assertTrue(page.getByText("Workflow test").first().isVisible(),
                "The student must see the published result");
        assertTrue(page.getByText("80.00").first().isVisible(),
                "The student must see their own mark");

        // The timetable and the attendance of their own class.
        gotoPage("/student/timetable");
        assertTrue(page.getByText("Workflow Subject").first().isVisible(),
                "The student must see the timetable of their own class");
        gotoPage("/student/attendance");
        assertTrue(page.getByText("Workflow Student").first().isVisible(),
                "The student must see their own attendance");
        assertTrue(page.getByText("1").first().isVisible(),
                "The student must see their own present count");

        // The fees of their own agreement.
        gotoPage("/student/fees");
        assertTrue(page.getByText("12000").first().isVisible(),
                "The student must see their own agreed fee");
        assertTrue(page.getByText("12000.00").first().isVisible(),
                "The student must see the agreed amount of their own agreement");

        // Screenshots of the new screens (the rendering, not only the data).
        gotoPage("/student");
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(ARTIFACTS_DIR.resolve("student-dashboard.png")
                        .toAbsolutePath().normalize())
                .setFullPage(true));
        // A student cannot reach the management screens.
        gotoPage("/fees");
        assertTrue(page.getByText("You do not have access to that feature").first().isVisible(),
                "A student must not reach the fee management");

        assertNoClientOrServerErrors(403);
    }

    @Test
    void aTeacherSeesOnlyTheirOwnClasses() {
        var tree = seedBaseTree("WorkflowGuard");
        var student = trackedStudent("Workflow Guard Student");
        var enrollment = trackedEnrollment(student, tree.batch());

        var ownUser = trackedTeacherUser("workflow.guard.own");
        var ownProfile = trackedTeacherProfile(ownUser, "Workflow Guard Owner");
        fixtures.assignTeacher(tree.batchSubject(), ownProfile);

        // A second class of another teacher.
        var otherSubject = fixtures.subject("Workflow Guard Other Subject");
        var otherCourseSubject = fixtures.assign(tree.course(), otherSubject);
        trackedSubjectIds.add(otherSubject.getId());
        trackedCourseSubjectIds.add(otherCourseSubject.getId());
        var otherAssignment = trackedBatchSubject(tree.batch(), otherCourseSubject);
        var otherUser = trackedTeacherUser("workflow.guard.other");
        var otherProfile = trackedTeacherProfile(otherUser, "Workflow Guard Outsider");
        fixtures.assignTeacher(otherAssignment, otherProfile);

        loginAdmin();
        gotoPage("/timetable/new");
        page.locator("#batchSubjectId").selectOption(String.valueOf(otherAssignment.getId()));
        page.locator("#dayOfWeek").selectOption("TUESDAY");
        page.locator("#startTime").fill("11:00");
        page.locator("#endTime").fill("12:00");
        page.locator("#effectiveFrom").fill(today().toString());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create timetable entry"))
                .click();
        assertFlash("Timetable entry created.");

        // The other teacher must not see the class of the first one.
        logout();
        loginTeacher(ownUser);
        gotoPage("/timetable");
        assertEquals(0, page.getByText("Workflow Guard Other Subject").count(),
                "A teacher must not see the classes of another teacher");

        assertNoClientOrServerErrors();
    }
}
