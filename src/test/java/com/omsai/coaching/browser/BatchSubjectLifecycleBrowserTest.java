package com.omsai.coaching.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.omsai.coaching.attendance.ClassSessionService;
import com.omsai.coaching.attendance.SessionForm;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.security.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-browser verification of the batch-subject lifecycle.
 *
 * <p>The journey the institute reported: a class is created for a
 * subject of a batch, and afterwards the subject can no longer be
 * removed from that batch. The page must not offer a delete button
 * that the backend silently rejects, and it must offer the action that
 * really works - deactivate - together with the reason.</p>
 */
class BatchSubjectLifecycleBrowserTest extends BrowserTestBase {

    @Autowired
    private ClassSessionService sessionService;

    @Test
    void aSubjectWithClassesIsDeactivatedInsteadOfRemoved() {
        BaseTree tree = seedBaseTree("Lifecycle");
        UserAccount admin = trackedAdminUser("lifecycle.admin");
        createClass(tree.batchSubject(), admin);

        login(admin.getEmail(), fixtures.safePassword());

        gotoPage("/batches/" + tree.batch().getId());

        // The subject has a class, so it cannot be deleted any more.
        // The page must offer the action that really works instead of a
        // delete button the backend would silently reject.
        assertTrue(page.locator("button[title='Deactivate subject']").count() == 1,
                "The page must offer a Deactivate action for a subject that has classes.");

        // An active subject must not also offer "Activate" - two
        // contradictory actions on one row would be confusing.
        assertTrue(page.locator("button[title='Activate subject']").count() == 0,
                "An active subject must not offer an Activate action.");

        assertTrue(page.getByText("Cannot be removed:", new Page.GetByTextOptions()
                        .setExact(false)).first().isVisible(),
                "The page must explain why the subject cannot be removed.");

        // No misleading remove button next to the deactivate action.
        assertTrue(page.locator("button[title='Remove subject']").count() == 0,
                "A subject with classes must not offer a Remove button.");

        // Deactivating works and is confirmed.
        page.locator("button[title='Deactivate subject']").first().click();
        page.waitForLoadState(LoadState.LOAD);

        assertFlash("Subject deactivated");
        assertTrue(page.getByText("Deactivated", new Page.GetByTextOptions().setExact(false)).first().isVisible(),
                "A deactivated subject must be marked as such.");

        // A deactivated subject can be activated again - and only then.
        assertTrue(page.locator("button[title='Activate subject']").count() == 1,
                "A deactivated subject must offer an Activate action.");
        assertTrue(page.locator("button[title='Deactivate subject']").count() == 0,
                "A deactivated subject must not offer a Deactivate action.");

        // Visual evidence for the audit: the batch page after the repair.
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(ARTIFACTS_DIR.resolve("batch-subject-lifecycle.png")
                        .toAbsolutePath().normalize())
                .setFullPage(true));

        assertNoClientOrServerErrors();
    }

    @Test
    void deactivatingASubjectKeepsItsClassesAndAttendance() {
        BaseTree tree = seedBaseTree("KeepClasses");
        UserAccount admin = trackedAdminUser("keepclasses.admin");
        createClass(tree.batchSubject(), admin);

        login(admin.getEmail(), fixtures.safePassword());
        gotoPage("/batches/" + tree.batch().getId());

        page.locator("button[title='Deactivate subject']").first().click();
        page.waitForLoadState(LoadState.LOAD);
        assertFlash("Subject deactivated");

        // The class itself is still there and still reachable.
        gotoPage("/sessions");
        assertTrue(page.getByText(tree.subject().getName()).first().isVisible(),
                "The class of a deactivated subject must still be listed.");

        assertNoClientOrServerErrors();
    }

    @Test
    void aSubjectThatWasNeverUsedCanStillBeRemoved() {
        BaseTree tree = seedBaseTree("Unused");
        UserAccount admin = trackedAdminUser("unused.admin");

        login(admin.getEmail(), fixtures.safePassword());
        gotoPage("/batches/" + tree.batch().getId());

        // Nothing depends on it, so the real remove action is offered.
        assertTrue(page.locator("button[title='Remove subject']").count() == 1,
                "A subject that was never used must offer the Remove action.");

        page.locator("button[title='Remove subject']").first().click();
        page.waitForLoadState(LoadState.LOAD);

        assertFlash("Subject removed from the batch");
        assertNoClientOrServerErrors();
    }

    // ----- helpers ---------------------------------------------------------

    private void createClass(BatchSubject assignment, UserAccount admin) {
        SessionForm form = new SessionForm();
        form.setBatchSubjectId(assignment.getId());
        form.setSessionDate(LocalDate.of(2026, 9, 7));
        form.setStartTime(LocalTime.of(10, 0));
        form.setEndTime(LocalTime.of(11, 0));
        form.setTopic("Algebra");
        sessionService.create(form, admin);
    }
}
