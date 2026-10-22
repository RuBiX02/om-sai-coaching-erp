package com.omsai.coaching.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the redesigned administrator shell and dashboard in a real
 * browser.
 *
 * <p>The test checks the things a user would notice: that the grouped
 * navigation is present and reaches its destinations, that the active
 * page is marked, that the dashboard shows real figures rather than
 * placeholders, that the header actions reach their destinations, that
 * the layout adapts to a tablet and a phone, and that nothing overflows
 * horizontally or produces a console error.</p>
 *
 * <p>Screenshots are written to the project's usual evidence folder
 * ({@code target/browser-artifacts}).</p>
 */
@Tag("browser")
class AdminDashboardDesignBrowserTest extends BrowserTestBase {

    private static final Path SHOTS = ARTIFACTS_DIR;

    private void shoot(String name, int width, int height) {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(SHOTS.resolve(name).toAbsolutePath().normalize())
                .setFullPage(true));
    }

    /** True when the page scrolls sideways - a layout defect on any width. */
    private boolean hasHorizontalOverflow() {
        Object result = page.evaluate(
                "() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1");
        return Boolean.TRUE.equals(result);
    }

    @Test
    void theAdminShellShowsThePremiumNavigationAndReachesItsDestinations() {
        loginAdmin();

        // --- brand ---------------------------------------------------------
        // The labels are rendered in small capitals, so the comparison
        // ignores the case the CSS applies.
        assertEquals("om sai coaching",
                page.locator(".app-brand .brand-name").innerText().trim().toLowerCase(),
                "The sidebar must carry the institute name");
        assertEquals("management erp",
                page.locator(".app-brand .brand-sub").innerText().trim().toLowerCase(),
                "The sidebar must carry the product label");
        assertTrue(page.locator(".app-brand .brand-mark").count() == 1,
                "The sidebar must carry the institute emblem");

        // --- navigation groups ---------------------------------------------
        assertTrue(page.locator(".app-sidebar .nav-group-title").count() >= 3,
                "The navigation must be grouped into sections");
        assertTrue(page.locator(".app-sidebar .nav-group-title",
                        new Page.LocatorOptions().setHasText("People")).isVisible(),
                "The people group heading must be visible");
        assertTrue(page.locator(".app-sidebar .nav-group-title",
                        new Page.LocatorOptions().setHasText("Academics")).isVisible(),
                "The academics group heading must be visible");
        assertTrue(page.locator(".app-sidebar .nav-group-title",
                        new Page.LocatorOptions().setHasText("Finance")).isVisible(),
                "The finance group heading must be visible");

        // --- active state ---------------------------------------------------
        String active = page.locator(".app-sidebar .nav-link.active").innerText().trim();
        assertTrue(active.contains("Dashboard"),
                "The current page must be marked in the navigation, got: " + active);
        assertEquals("page",
                page.locator(".app-sidebar .nav-link.active").first().getAttribute("aria-current"),
                "The active navigation entry must be announced as the current page");

        // --- the top bar ------------------------------------------------------
        assertTrue(page.locator(".app-topbar h1").innerText().trim()
                        .equals("Administrator Dashboard"),
                "The top bar must name the current page");
        assertTrue(page.locator(".topbar-identity").isVisible(),
                "The signed-in identity must be visible in the top bar");
        assertTrue(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign out"))
                        .isVisible(),
                "Sign out must stay reachable in the top bar");

        // --- every navigation destination still resolves ----------------------
        record Target(String link, String path) {}
        for (Target target : new Target[] {
                new Target("Dashboard", "/dashboard"),
                new Target("Students", "/students"),
                new Target("Teachers", "/teachers"),
                new Target("Courses", "/courses"),
                new Target("Subjects", "/subjects"),
                new Target("Batches", "/batches"),
                new Target("Timetable", "/timetable"),
                new Target("Class sessions", "/sessions"),
                new Target("Attendance reports", "/attendance/reports"),
                new Target("Examinations", "/examinations"),
                new Target("Fees", "/fees"),
                new Target("Installments", "/installments"),
                new Target("Payments", "/payments"),
                new Target("Accounts", "/users") }) {
            page.locator(".app-sidebar .nav-link",
                    new Page.LocatorOptions().setHasText(target.link())).first().click();
            page.waitForLoadState(LoadState.LOAD);
            assertTrue(page.url().contains(target.path()),
                    "Clicking '" + target.link() + "' must open " + target.path()
                            + " but landed on " + page.url());
        }
        assertNoClientOrServerErrors();
    }

    @Test
    void theDashboardShowsRealFiguresAndWorkingActions() {
        loginAdmin();

        // --- the greeting follows the business clock -------------------------
        String greeting = page.locator(".page-eyebrow").innerText().trim().toLowerCase();
        assertTrue(greeting.equals("good morning") || greeting.equals("good afternoon")
                        || greeting.equals("good evening"),
                "The dashboard must greet the administrator for the current time, got: " + greeting);
        assertTrue(page.getByText("Institute overview").first().isVisible(),
                "The dashboard must carry the page identity");
        assertTrue(page.locator(".page-header-meta").innerText().trim().length() > 8,
                "The dashboard must show the current date");

        // --- the summary cards hold live figures -----------------------------
        assertTrue(page.locator(".stat-value").count() >= 8,
                "The dashboard must render the summary figures");
        assertTrue(page.getByText("Class sessions today").first().isVisible(),
                "The sessions card must be labelled");
        assertTrue(page.getByText("Collections this month").first().isVisible(),
                "The collections card must be labelled");

        // The demo catalogue is seeded into this database, so the student,
        // teacher, course and batch figures must be real numbers - not the
        // "0" placeholder of the template.
        long students = Long.parseLong(
                page.locator(".metric-cell", new Page.LocatorOptions()
                                .setHasText("Active students"))
                        .locator(".stat-value").innerText().trim());
        assertTrue(students > 0,
                "The active-student figure must come from the database, got: " + students);

        // --- the header actions reach real destinations ------------------------
        for (String[] action : new String[][] {
                { "Register student", "/students/new" },
                { "Fee accounts", "/fees" },
                { "New session", "/sessions/new" } }) {
            // Each action leaves the dashboard, so it is opened again
            // before the next one is exercised.
            gotoPage("/dashboard");
            page.locator(".page-header a.btn", new Page.LocatorOptions().setHasText(action[0]))
                    .first().click();
            page.waitForLoadState(LoadState.LOAD);
            assertTrue(page.url().contains(action[1]),
                    "The header action '" + action[0] + "' must open " + action[1]
                            + " but landed on " + page.url());
        }

        // The attention panel links the collection reports: the card
        // header opens the overdue report, the due-soon entry opens the
        // upcoming report. The header actions above left the dashboard,
        // so it is opened again.
        gotoPage("/dashboard");
        assertTrue(page.locator(".card", new Page.LocatorOptions().setHasText("Needs attention"))
                        .locator("a[href='/installments/overdue']").count() >= 1,
                "The attention panel must link the overdue report");
        page.locator(".attention-item", new Page.LocatorOptions().setHasText("Due in the next 7 days"))
                .first().click();
        page.waitForLoadState(LoadState.LOAD);
        assertTrue(page.url().contains("/installments/upcoming"),
                "The due-soon attention entry must open the upcoming report but landed on "
                        + page.url());
        assertNoClientOrServerErrors();
    }

    @Test
    void theDashboardAdaptsToDesktopTabletAndMobileWithoutOverflow() {
        loginAdmin();

        // --- desktop ---------------------------------------------------------
        page.setViewportSize(1280, 900);
        gotoPage("/dashboard");
        assertTrue(page.locator(".app-sidebar").isVisible(),
                "The sidebar must stay visible on a desktop screen");
        assertFalse(hasHorizontalOverflow(), "The dashboard must not scroll sideways on a desktop");
        shoot("admin-dashboard-desktop.png", 1280, 900);

        // --- tablet ----------------------------------------------------------
        page.setViewportSize(820, 1180);
        gotoPage("/dashboard");
        assertFalse(hasHorizontalOverflow(), "The dashboard must not scroll sideways on a tablet");
        shoot("admin-dashboard-tablet.png", 820, 1180);

        // --- mobile ----------------------------------------------------------
        page.setViewportSize(390, 844);
        gotoPage("/dashboard");
        assertFalse(hasHorizontalOverflow(), "The dashboard must not scroll sideways on a phone");
        // The navigation becomes a drawer that is opened from the top bar.
        assertTrue(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                        .setName("Open navigation menu")).isVisible(),
                "A phone must get a control that opens the navigation");
        shoot("admin-dashboard-mobile.png", 390, 844);

        // The drawer must open and offer the navigation.
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName("Open navigation menu")).click();
        page.locator(".app-sidebar .nav-link",
                new Page.LocatorOptions().setHasText("Students")).first().click();
        page.waitForLoadState(LoadState.LOAD);
        assertTrue(page.url().contains("/students"),
                "The mobile drawer must lead to the students list, got: " + page.url());
        assertNoClientOrServerErrors();
    }
}
