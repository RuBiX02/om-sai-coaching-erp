package com.omsai.coaching.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-browser tests for authentication: successful login, rejected
 * credentials, the authentication wall and session invalidation on
 * logout.
 */
@Tag("browser")
class AuthenticationBrowserTest extends BrowserTestBase {

    @Test
    void administratorLogsInWithValidCredentialsAndSeesTheDashboard() {
        loginAdmin();

        assertTrue(page.locator(".app-sidebar").isVisible(),
                "Admin dashboard must render the app sidebar");
        assertTrue(page.locator(".stat-value").count() >= 8,
                "Dashboard should render the Phase 1 + Phase 2 stat cards");
        assertTrue(page.getByText("Class sessions today").first().isVisible());
        assertTrue(page.getByText("Collections this month").first().isVisible());
        assertNoClientOrServerErrors();
    }

    @Test
    void invalidCredentialsAreRejectedWithAVisibleError() {
        gotoPage("/login");
        page.locator("#username").fill(ADMIN_EMAIL);
        page.locator("#password").fill("Definitely-Not-The-Password");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();

        assertTrue(page.url().contains("/login?error"),
                "Failed sign-in must bounce back to /login?error, got " + page.url());
        assertTrue(page.getByText("Invalid email or password.").first().isVisible(),
                "The login page must show the invalid-credentials alert");
        assertNoClientOrServerErrors();
    }

    @Test
    void protectedPagesRedirectUnauthenticatedVisitorsToTheLoginScreen() {
        List<String> protectedPaths = List.of(
                "/", "/dashboard", "/students", "/courses", "/sessions", "/payments");
        for (String path : protectedPaths) {
            gotoPage(path);
            assertTrue(page.url().contains("/login"),
                    "Unauthenticated GET " + path + " must be redirected to /login, got " + page.url());
            assertTrue(page.getByRole(AriaRole.BUTTON,
                            new Page.GetByRoleOptions().setName("Sign in")).isVisible(),
                    "The login form must be shown for " + path);
        }
        assertNoClientOrServerErrors();
    }

    @Test
    void logoutInvalidatesTheSession() {
        loginAdmin();

        // Sign out through the rendered topbar button (CSRF form POST).
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign out")).click();
        assertTrue(page.url().contains("/login?logout"),
                "Sign out must land on the logged-out login screen, got " + page.url());

        // The invalidated session must no longer reach protected pages.
        gotoPage("/dashboard");
        assertTrue(page.url().contains("/login"),
                "After logout, /dashboard must be redirected to the login page, got " + page.url());
        assertNoClientOrServerErrors();
    }
}
