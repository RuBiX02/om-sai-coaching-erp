package com.omsai.coaching.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.omsai.coaching.fees.FeeAccount;
import com.omsai.coaching.fees.FeeAccountForm;
import com.omsai.coaching.fees.FeeAccountService;
import com.omsai.coaching.security.UserAccount;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-browser tests of editing an installment plan: the administrator
 * corrects a schedule through the rendered form, an invalid schedule is
 * rejected without touching the original one, and the editing action
 * disappears once a payment has been booked against the plan.
 */
@Tag("browser")
class InstallmentPlanEditBrowserTest extends BrowserTestBase {

    @Autowired
    private FeeAccountService feeAccountService;

    private static final String NET_FEE = "30000";

    /** A fee account with a two-row plan for a tracked student. */
    private FeeAccount accountWithPlan(UserAccount admin, long enrollmentId) {
        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(enrollmentId);
        form.setGrossFee(new BigDecimal(NET_FEE));
        form.setAcademicYear("2026-27");
        FeeAccount account = feeAccountService.create(form, admin);
        gotoPage("/fees/" + account.getId() + "/installments");
        page.locator("input[name='dueDate']").nth(0).fill(today().plusDays(10).toString());
        page.locator("input[name='amount']").nth(0).fill("10000");
        page.locator("input[name='dueDate']").nth(1).fill(today().plusDays(40).toString());
        page.locator("input[name='amount']").nth(1).fill("20000");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create installment plan"))
                .click();
        assertFlash("Installment plan created.");
        return account;
    }

    @Test
    void theAdministratorEditsTheScheduleThroughTheRealInterface() {
        var tree = seedBaseTree("PlanEditAdmin");
        var student = trackedStudent("Plan Edit Browser Student");
        var enrollment = trackedEnrollment(student, tree.batch());

        loginAdmin();
        UserAccount admin = trackedAdminUser("browser-plan-edit");
        FeeAccount account = accountWithPlan(admin, enrollment.getId());

        // The fee account offers the edit action while the plan is editable.
        gotoPage("/fees/" + account.getId());
        assertTrue(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
                        .setName("Edit installment plan")).first().isVisible(),
                "The fee account must offer the edit action");

        // The edit form is loaded with the current schedule.
        gotoPage("/fees/" + account.getId() + "/installments/edit");
        assertTrue(page.getByText("Edit the installment schedule").first().isVisible(),
                "The edit form must be shown");
        assertEquals("10000.00", page.locator("input[name='amount']").nth(0).inputValue(),
                "The form must be loaded with the current amount");
        assertEquals(today().plusDays(10).toString(),
                page.locator("input[name='dueDate']").nth(0).inputValue(),
                "The form must be loaded with the current due date");

        // The administrator corrects the schedule: a different split and
        // a different due date.
        page.locator("input[name='amount']").nth(0).fill("12000");
        page.locator("input[name='dueDate']").nth(0).fill(today().plusDays(15).toString());
        page.locator("input[name='amount']").nth(1).fill("18000");

        // The preview shows the proposed total and the difference.
        assertEquals("30000.00", page.locator("#js-scheduled-total").innerText(),
                "The preview must show the proposed total");
        assertEquals("0.00", page.locator("#js-difference").innerText(),
                "The preview must show no difference to the net fee");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save the new schedule"))
                .click();
        assertFlash("Installment plan updated.");

        // The fee account shows the new schedule.
        gotoPage("/fees/" + account.getId());
        String table = page.locator("table").first().innerText().replaceAll("\\s+", " ");
        assertTrue(table.contains("12000") || table.contains("12,000"),
                "The corrected amount must be shown, got: " + table);
        assertTrue(table.contains("18000") || table.contains("18,000"),
                "The corrected amount must be shown, got: " + table);
        // The fee agreement itself is unchanged.
        assertTrue(page.getByText("30000").first().isVisible(),
                "The net fee must still be unchanged");

        assertNoClientOrServerErrors();
    }

    @Test
    void anInvalidScheduleIsRejectedWithoutChangingTheOriginalPlan() {
        var tree = seedBaseTree("PlanEditReject");
        var student = trackedStudent("Plan Edit Reject Student");
        var enrollment = trackedEnrollment(student, tree.batch());

        loginAdmin();
        UserAccount admin = trackedAdminUser("browser-plan-reject");
        FeeAccount account = accountWithPlan(admin, enrollment.getId());

        gotoPage("/fees/" + account.getId() + "/installments/edit");
        page.locator("input[name='amount']").nth(0).fill("5000");
        page.locator("input[name='amount']").nth(1).fill("5000");

        // The preview already says that the schedule does not add up.
        assertTrue(page.getByText("must equal the net fee").first().isVisible(),
                "The preview must warn that the schedule does not add up");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save the new schedule"))
                .click();

        assertTrue(page.getByText("must add up to exactly the net fee").first().isVisible(),
                "The server must refuse the schedule with an explanation");

        // The original schedule is untouched.
        gotoPage("/fees/" + account.getId());
        String table = page.locator("table").first().innerText().replaceAll("\\s+", " ");
        assertTrue(table.contains("10000") || table.contains("10,000"),
                "The original schedule must be untouched, got: " + table);
        assertTrue(!table.contains("5000"),
                "The rejected amounts must not have been saved, got: " + table);

        assertNoClientOrServerErrors();
    }

    @Test
    void theEditingActionDisappearsOnceAPaymentHasBeenBooked() {
        var tree = seedBaseTree("PlanEditLocked");
        var student = trackedStudent("Plan Edit Locked Student");
        var enrollment = trackedEnrollment(student, tree.batch());

        loginAdmin();
        UserAccount admin = trackedAdminUser("browser-plan-locked");
        FeeAccount account = accountWithPlan(admin, enrollment.getId());

        // A payment is booked against the plan.
        gotoPage("/payments/new?feeAccountId=" + account.getId());
        page.locator("#amount").fill("5000");
        page.locator("#paymentDate").fill(today().toString());
        page.locator("#paymentMethod").selectOption("CASH");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Record payment")).click();
        assertTrue(page.url().contains("/receipt"),
                "After the payment the receipt is shown, got " + page.url());

        // The fee account no longer offers the edit action and explains why.
        gotoPage("/fees/" + account.getId());
        assertEquals(0, page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
                        .setName("Edit installment plan")).count(),
                "The edit action must disappear once a payment has been booked");
        assertTrue(page.getByText("payment-allocation history exists").first().isVisible(),
                "The page must explain why the schedule can no longer be edited");

        // The direct URL is refused as well, with the same explanation.
        gotoPage("/fees/" + account.getId() + "/installments/edit");
        assertTrue(page.getByText("payment-allocation history exists").first().isVisible(),
                "The direct edit URL must be refused with an explanation");

        assertNoClientOrServerErrors();
    }
}
