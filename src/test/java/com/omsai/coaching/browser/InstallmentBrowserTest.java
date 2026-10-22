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
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-browser tests of the installment module: creating a plan
 * through the form, the automatic allocation of a payment, the
 * reports, the effect of voiding a payment and the permission wall
 * around teachers.
 */
@Tag("browser")
class InstallmentBrowserTest extends BrowserTestBase {

    @Autowired
    private FeeAccountService feeAccountService;

    private static final String NET_FEE = "30000";

    /** A fee account for a tracked student (the suite cleans it up). */
    private FeeAccount feeAccount(UserAccount admin, long studentId, long enrollmentId) {
        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(enrollmentId);
        form.setGrossFee(new BigDecimal(NET_FEE));
        form.setAcademicYear("2026-27");
        return feeAccountService.create(form, admin);
    }

    @Test
    void theAdministratorCreatesAnInstallmentPlanAndSeesTheBalances() {
        var tree = seedBaseTree("InstallmentAdmin");
        var student = trackedStudent("Installment Browser Student");
        var enrollment = trackedEnrollment(student, tree.batch());

        loginAdmin();
        FeeAccount account = feeAccount(adminUser(), student.getId(), enrollment.getId());

        // The fee account offers the plan.
        gotoPage("/fees/" + account.getId());
        assertTrue(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
                        .setName("Create installment plan")).first().isVisible(),
                "The fee account must offer the installment plan");

        // The plan form: three rows, amounts that add up to the net fee.
        gotoPage("/fees/" + account.getId() + "/installments");
        assertTrue(page.getByText("Installment plan").first().isVisible(),
                "The plan form must be shown");

        page.locator("input[name='installmentNumber']").nth(0).fill("1");
        page.locator("input[name='dueDate']").nth(0).fill(today().plusDays(10).toString());
        page.locator("input[name='amount']").nth(0).fill("10000");
        page.locator("input[name='installmentNumber']").nth(1).fill("2");
        page.locator("input[name='dueDate']").nth(1).fill(today().plusDays(40).toString());
        page.locator("input[name='amount']").nth(1).fill("10000");
        // A third row is added by the browser, like an administrator would.
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add installment")).click();
        assertEquals(3, page.locator("tr.installment-row").count(),
                "The add button must add a schedule row");
        page.locator("input[name='installmentNumber']").nth(2).fill("3");
        page.locator("input[name='dueDate']").nth(2).fill(today().plusDays(70).toString());
        page.locator("input[name='amount']").nth(2).fill("10000");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create installment plan"))
                .click();
        assertFlash("Installment plan created.");

        // The fee account shows the schedule with the derived balances.
        String table = page.locator("table").first().innerText().replaceAll("\\s+", " ");
        assertTrue(table.contains("10,000") || table.contains("10000"),
                "The schedule must show the scheduled amounts, got: " + table);
        assertTrue(table.contains("UPCOMING") || table.contains("DUE_TODAY"),
                "The schedule must show the derived status, got: " + table);
        assertTrue(page.getByText("30000").first().isVisible(),
                "The net fee must still be unchanged");

        assertNoClientOrServerErrors();
    }

    @Test
    void aScheduleThatDoesNotAddUpIsRejected() {
        var tree = seedBaseTree("InstallmentReject");
        var student = trackedStudent("Installment Reject Student");
        var enrollment = trackedEnrollment(student, tree.batch());

        loginAdmin();
        FeeAccount account = feeAccount(adminUser(), student.getId(), enrollment.getId());

        gotoPage("/fees/" + account.getId() + "/installments");
        page.locator("input[name='dueDate']").nth(0).fill(today().plusDays(10).toString());
        page.locator("input[name='amount']").nth(0).fill("10000");
        page.locator("input[name='dueDate']").nth(1).fill(today().plusDays(40).toString());
        page.locator("input[name='amount']").nth(1).fill("5000");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create installment plan"))
                .click();

        assertTrue(page.getByText("must add up to exactly the net fee").first().isVisible(),
                "The mismatch must be reported on the form");
        assertNoClientOrServerErrors();
    }

    @Test
    void aPaymentIsAllocatedToTheEarliestInstallmentAndTheReportsFollow() {
        var tree = seedBaseTree("InstallmentPay");
        var student = trackedStudent("Installment Pay Student");
        var enrollment = trackedEnrollment(student, tree.batch());

        loginAdmin();
        FeeAccount account = feeAccount(adminUser(), student.getId(), enrollment.getId());

        gotoPage("/fees/" + account.getId() + "/installments");
        page.locator("input[name='dueDate']").nth(0).fill(today().plusDays(10).toString());
        page.locator("input[name='amount']").nth(0).fill("15000");
        page.locator("input[name='dueDate']").nth(1).fill(today().plusDays(40).toString());
        page.locator("input[name='amount']").nth(1).fill("15000");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create installment plan"))
                .click();
        assertFlash("Installment plan created.");

        // A payment of 2500: it covers the first installment only.
        gotoPage("/payments/new?feeAccountId=" + account.getId());
        page.locator("#amount").fill("2500");
        page.locator("#paymentDate").fill(today().toString());
        page.locator("#paymentMethod").selectOption("CASH");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Record payment")).click();
        assertTrue(page.url().contains("/receipt"),
                "After the payment the receipt is shown, got " + page.url());

        gotoPage("/fees/" + account.getId());
        String table = page.locator("table").first().innerText().replaceAll("\\s+", " ");
        assertTrue(table.contains("PARTIALLY_PAID"),
                "The first installment must be partly paid, got: " + table);
        assertTrue(table.contains("2500"), "The paid amount must be shown, got: " + table);
        assertTrue(table.contains("7500"), "The remaining amount must be shown, got: " + table);

        // The reports see the same data.
        gotoPage("/installments");
        assertTrue(page.getByText("Total outstanding").first().isVisible(),
                "The installment overview must be shown");
        assertTrue(page.getByText(student.getFullName()).first().isVisible(),
                "The overview must list the student with the next installment due");

        gotoPage("/installments/upcoming");
        assertTrue(page.getByText(student.getFullName()).first().isVisible(),
                "The upcoming report must list the student");
        gotoPage("/installments/overdue");
        assertTrue(page.getByText("No installment is overdue").first().isVisible(),
                "Nothing is overdue yet");

        assertNoClientOrServerErrors();
    }

    @Test
    void voidingAnAllocatedPaymentReturnsTheBalance() {
        var tree = seedBaseTree("InstallmentVoid");
        var student = trackedStudent("Installment Void Student");
        var enrollment = trackedEnrollment(student, tree.batch());

        loginAdmin();
        FeeAccount account = feeAccount(adminUser(), student.getId(), enrollment.getId());

        gotoPage("/fees/" + account.getId() + "/installments");
        page.locator("input[name='dueDate']").nth(0).fill(today().plusDays(10).toString());
        page.locator("input[name='amount']").nth(0).fill("15000");
        page.locator("input[name='dueDate']").nth(1).fill(today().plusDays(40).toString());
        page.locator("input[name='amount']").nth(1).fill("15000");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create installment plan"))
                .click();
        assertFlash("Installment plan created.");

        gotoPage("/payments/new?feeAccountId=" + account.getId());
        page.locator("#amount").fill("6000");
        page.locator("#paymentDate").fill(today().toString());
        page.locator("#paymentMethod").selectOption("CASH");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Record payment")).click();
        gotoPage("/fees/" + account.getId());

        String before = page.locator("table").first().innerText().replaceAll("\\s+", " ");
        assertTrue(before.contains("6000"), "The payment must be allocated, got: " + before);

        // Void the payment: the installment balance must come back.
        page.locator("details summary[title='Void payment']").first().click();
        page.locator("textarea[name='reason']").first().fill("wrong entry");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Void payment")).click();
        assertFlash("voided. The outstanding balance has been recalculated");

        String after = page.locator("table").first().innerText().replaceAll("\\s+", " ");
        assertTrue(after.contains("15000.00"),
                "The installment must be outstanding again, got: " + after);
        assertTrue(!after.contains("PARTIALLY_PAID"),
                "The installment must not be partly paid any more, got: " + after);
        assertTrue(after.contains("UPCOMING"),
                "The installment must show its derived status, got: " + after);

        assertNoClientOrServerErrors();
    }

    @Test
    void teachersCannotReachTheInstallmentScreens() {
        var tree = seedBaseTree("InstallmentGuard");
        var student = trackedStudent("Installment Guard Student");
        var enrollment = trackedEnrollment(student, tree.batch());

        loginAdmin();
        FeeAccount account = feeAccount(adminUser(), student.getId(), enrollment.getId());

        var teacherUser = trackedTeacherUser("installment.browser.teacher");
        trackedTeacherProfile(teacherUser, "Installment Browser Teacher");

        logout();
        loginTeacher(teacherUser);

        for (String path : java.util.List.of("/installments", "/installments/upcoming",
                "/installments/overdue", "/fees/" + account.getId() + "/installments")) {
            gotoPage(path);
            assertTrue(page.getByText("You do not have access to that feature").first().isVisible(),
                    "Teachers must be denied " + path + " (" + page.url() + ")");
        }
        assertEquals(0, page.getByRole(AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("Installments")).count(),
                "A teacher must not see the installment link");

        assertNoClientOrServerErrors(403);
    }

    /** A tracked administrator account (the audit field of the agreement). */
    private UserAccount adminUser() {
        return trackedAdminUser("browser-installment");
    }
}
