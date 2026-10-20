package com.omsai.coaching.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-browser tests for the core management pages: dashboard content,
 * sidebar navigation, form validation errors and a complete CRUD
 * write-through (create a course through the form, see it in the list).
 */
@Tag("browser")
class CorePagesBrowserTest extends BrowserTestBase {

    @Test
    void everySidebarLinkLeadsToItsManagementPage() {
        loginAdmin();

        record NavTarget(String link, String path, String heading) {}
        List<NavTarget> targets = List.of(
                new NavTarget("Dashboard", "/dashboard", "Administrator Dashboard"),
                new NavTarget("Class sessions", "/sessions", "Class sessions"),
                new NavTarget("Courses", "/courses", "Courses"),
                new NavTarget("Subjects", "/subjects", "Subjects"),
                new NavTarget("Batches", "/batches", "Batches"),
                new NavTarget("Students", "/students", "Students"),
                new NavTarget("Teachers", "/teachers", "Teachers"),
                new NavTarget("Attendance reports", "/attendance/reports", "Attendance reports"),
                new NavTarget("Fees", "/fees", "Fee agreements"),
                new NavTarget("Payments", "/payments", "Payments"),
                new NavTarget("Accounts", "/users", "Sign-in accounts"));

        // Start from the dashboard itself so each link is exercised exactly once.
        for (NavTarget target : targets) {
            page.locator(".app-sidebar .nav-link", new Page.LocatorOptions()
                    .setHasText(target.link())).first().click();
            page.waitForLoadState();
            assertTrue(page.url().contains(target.path()),
                    "Clicking '" + target.link() + "' must open " + target.path()
                            + " but landed on " + page.url());
            assertEquals(target.heading(),
                    page.locator(".app-topbar h1").innerText().trim(),
                    "The topbar heading for " + target.path() + " should read '" + target.heading() + "'");
        }
        assertNoClientOrServerErrors();
    }

    @Test
    void courseFormRejectsAnEmptyNameButCreatesAValidCourse() {
        loginAdmin();

        // Empty required field → server-side validation error stays on the form.
        gotoPage("/courses/new");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create course")).click();
        assertTrue(page.locator("#nameError").isVisible(),
                "An empty course name must show a validation error under the field");
        assertFalse(page.locator(".app-alerts .alert-success").isVisible(),
                "An invalid submission must not produce a success flash");

        // Valid submission → success flash and the course listed.
        String courseName = "BTech Physics " + System.nanoTime() % 100000;
        page.locator("#name").fill(courseName);
        page.locator("#category").fill("Competitive");
        page.locator("#durationMonths").fill("12");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create course")).click();
        assertFlash("Course created.");
        // The creation redirects to /courses/{id}; remember the row so the
        // after-each cleanup can delete this UI-created course as well.
        assertTrue(page.url().matches(".*/courses/\\d+"),
                "The creation must land on the new course's page, got " + page.url());
        trackedCourseIds.add(Long.parseLong(page.url().replaceAll(".*/courses/", "")));

        // The creation lands on the course detail page (topbar shows the name);
        // the list must include it.
        assertTrue(page.locator(".app-topbar h1").innerText().trim().contains(courseName),
                "The course detail page must be opened after creation");
        gotoPage("/courses");
        assertTrue(page.getByText(courseName).first().isVisible(),
                "The newly created course must appear in the course list");
        assertNoClientOrServerErrors();
    }

    @Test
    void seededRecordsAreVisibleAndSearchableOnListPages() {
        var tree = seedBaseTree("CorePages");
        var student = trackedStudent("Core Pages Wanderer");
        trackedEnrollment(student, tree.batch());

        loginAdmin();

        // Students page: the search box surfaces the seeded student by name fragment.
        gotoPage("/students?q=Wanderer");
        assertTrue(page.getByText(student.getFullName()).first().isVisible(),
                "Searching by a name fragment must surface the seeded student");
        // Negative control: an unrelated fragment must not match anything.
        gotoPage("/students?q=UnrelatedNothing");
        assertEquals(0, page.getByText(student.getFullName()).count(),
                "An unrelated search must not match the seeded student");

        // Batch and course lists show the seeded rows.
        gotoPage("/batches");
        assertTrue(page.getByText(tree.batch().getName()).first().isVisible());
        gotoPage("/courses");
        assertTrue(page.getByText(tree.course().getName()).first().isVisible());
        assertNoClientOrServerErrors();
    }
}
