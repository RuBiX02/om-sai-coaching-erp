package com.omsai.coaching.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.Media;
import com.omsai.coaching.student.Student;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-browser tests for fee agreements, payments and printable
 * receipts: a full admin journey from the student page to a partial
 * payment and its receipt (screen + print media + generated PDF), the
 * void workflow with the VOIDED stamp, and the permission walls that
 * keep teachers out of the fee module.
 *
 * <p>Receipt page numbers render through the server; the printed
 * artifacts (screenshots and PDFs) are written to
 * {@code target/browser-artifacts} for manual inspection and kept by
 * the build as debugging evidence.</p>
 */
@Tag("browser")
class FeesAndReceiptBrowserTest extends BrowserTestBase {

    /** 12000 gross − 2000 discount = 10000 payable. */
    private static final String GROSS_FEE = "12000";
    private static final String DISCOUNT = "2000";

    /** Local alias to avoid importing all five fixture entities here. */
    private record BaseTreeSource(
            com.omsai.coaching.course.Course course,
            com.omsai.coaching.subject.Subject subject,
            com.omsai.coaching.batch.CourseSubject courseSubject,
            com.omsai.coaching.batch.Batch batch,
            com.omsai.coaching.batch.BatchSubject batchSubject) {}

    @Test
    void partialPaymentUpdatesTheOutstandingBalanceAndIssuesCorrectReceipts() throws Exception {
        var tree = seedWithStudent("Fee browser", "Fee Browser Student");

        loginAdmin();

        // --- the fee agreement is created from the student's detail page ----
        gotoPage("/students/" + tree.student().getId());
        assertTrue(page.getByText(tree.student().getFullName()).first().isVisible(),
                "The student detail page must open");
        page.locator("a[title='Create fee agreement for this enrollment']").first().click();
        assertTrue(page.url().contains("/fees/new"),
                "The student page must link to the fee agreement form, got " + page.url());

        page.locator("#grossFee").fill(GROSS_FEE);
        page.locator("#discountAmount").fill(DISCOUNT);
        page.locator("#dueDate").fill(today().plusDays(30).toString());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create agreement")).click();
        assertFlash("Fee agreement created.");
        String feeAccountUrl = page.url();
        assertTrue(feeAccountUrl.matches(".*/fees/\\d+"),
                "After creation the browser must open the fee account, got " + feeAccountUrl);

        // Net fee shown on the account page; nothing paid yet.
        gotoPage(feeAccountUrl);
        String netFee = valueOf("dd", "Net fee");
        assertTrue(netFee.contains("10000"), "Net fee must be 10000 after the discount, saw '" + netFee + "'");

        // --- a partial payment lands directly on the receipt ------------------
        gotoPage(feeAccountUrl);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Record payment")).click();
        assertTrue(page.url().contains("/payments/new"),
                "The account page must link to the payment form, got " + page.url());
        page.locator("#amount").fill("4000");
        page.locator("#paymentDate").fill(today().toString());
        page.locator("#paymentMethod").selectOption("CASH");
        page.locator("#remarks").fill("First installment by browser test");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName("Record payment & open receipt")).click();

        // The receipt is a standalone page without the flash-alert fragment;
        // success is proven by the receipt content itself below.
        assertTrue(page.url().contains("/receipt"),
                "After recording, the browser must open the receipt, got " + page.url());
        String firstReceiptUrl = page.url();

        String receiptNumber = verifyReceiptContent(tree.student().getFullName());

        ScreenAndPdf paidReceipt =
                receiptPrintRendering(firstReceiptUrl, "receipt", tree.student().getFullName());
        assertTrue(paidReceipt.pdfText().contains(receiptNumber),
                "The generated PDF must contain the receipt number");
        assertTrue(paidReceipt.pdfText().contains(tree.student().getFullName()),
                "The generated PDF must contain the student name");
        assertEquals("none", paidReceipt.noPrintDisplay(),
                "Print media must hide the visible controls (.no-print)");

        // --- outstanding balance reflects the payment -------------------------
        gotoPage(feeAccountUrl);
        String outstandingNow = valueOf("dd", "Outstanding balance");
        assertTrue(outstandingNow.contains("6000"),
                "After paying 4000 of the 10000 payable, the outstanding must be 6000, saw "
                        + outstandingNow);

        assertEquals(0, serverErrors.size(), "The fee journey must not hit unexpected server errors");
    }

    @Test
    void voidedPaymentShowsItsStatusInLedgerAndOnTheReceipt() throws Exception {
        var tree = seedWithStudent("Void browser", "Void Browser Student");

        loginAdmin();
        String feeAccountUrl = createAgreementAndPay(tree, "5000");

        // Void via the ledger popover with a mandatory reason.
        gotoPage(feeAccountUrl);
        page.locator("details summary[title='Void payment']").first().click();
        page.locator("textarea[name='reason']").first()
                .fill("Wrong entry — recorded from the browser test");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Void payment")).click();
        assertFlash("voided. The outstanding balance has been recalculated");
        assertTrue(page.getByText("VOIDED", new Page.GetByTextOptions().setExact(true)).first().isVisible(),
                "The ledger must mark the voided payment as VOIDED");

        // The receipt renders a voided sheet with the stamp.
        page.locator("a[title='Receipt']").first().click();
        assertTrue(page.locator(".stamp-voided").first().isVisible(),
                "The voided receipt must show the VOIDED stamp");
        assertTrue(page.getByText("Wrong entry — recorded from the browser test").first().isVisible(),
                "The void reason must appear on the voided receipt");

        page.screenshot(new Page.ScreenshotOptions()
                .setPath(ARTIFACTS_DIR.resolve("receipt-voided-screen.png")
                        .toAbsolutePath().normalize())
                .setFullPage(true));

        // Print rendering keeps the VOIDED stamp and hides the controls.
        page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
        assertEquals("none",
                page.locator(".no-print").first().evaluate("el => getComputedStyle(el).display"),
                "Print media must hide the page controls on the voided receipt too");
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(ARTIFACTS_DIR.resolve("receipt-voided-print.png")
                        .toAbsolutePath().normalize())
                .setFullPage(true));
        Path voidedPdf = ARTIFACTS_DIR.resolve("receipt-voided-printed.pdf").toAbsolutePath().normalize();
        page.pdf(new Page.PdfOptions().setPath(voidedPdf));
        String voidedPdfText = pdfText(voidedPdf.toString());
        assertTrue(voidedPdfText.contains("VOIDED"), "The printed voided receipt must contain VOIDED");
        assertTrue(voidedPdfText.toLowerCase().contains(tree.student().getFullName().toLowerCase()),
                "The printed voided receipt must contain the student name");

        assertEquals(0, serverErrors.size(), "The void journey must not hit unexpected server errors");
    }

    @Test
    void teachersAreBlockedFromFeePaymentAndReportPages() {
        seedBaseTree("Fee Guard");
        var teacherUser = trackedTeacherUser("fees.guard.teacher");
        trackedTeacherProfile(teacherUser, "Fee Guard Teacher");

        loginTeacher(teacherUser);
        for (String path : List.of("/fees", "/payments", "/attendance/reports", "/users")) {
            gotoPage(path);
            assertTrue(page.getByText("You do not have access to that feature").first().isVisible(),
                    "Teachers must be denied " + path + " (landed on " + page.url() + ")");
        }
        assertNoClientOrServerErrors(403);
    }

    /* ----- helpers ---------------------------------------------------------- */

    /** Seed a full course tree plus a student with an active enrollment. */
    private StudentBundle seedWithStudent(String prefix, String studentName) {
        var tree = seedBaseTree(prefix + " Seeded");
        var student = trackedStudent(studentName);
        trackedEnrollment(student, tree.batch());
        BaseTreeSource source = new BaseTreeSource(tree.course(), tree.subject(),
                tree.courseSubject(), tree.batch(), tree.batchSubject());
        return new StudentBundle(source, student);
    }

    private record StudentBundle(BaseTreeSource tree, Student student) {}

    /** Creates the agreement (12000−2000) and records one payment. */
    private String createAgreementAndPay(StudentBundle bundle, String amount) {
        String studentUrl = "/students/" + bundle.student().getId();
        gotoPage(studentUrl);
        page.locator("a[title='Create fee agreement for this enrollment']").first().click();
        page.locator("#grossFee").fill(GROSS_FEE);
        page.locator("#discountAmount").fill(DISCOUNT);
        page.locator("#dueDate").fill(today().plusDays(30).toString());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create agreement")).click();
        String feeAccountUrl = page.url();
        gotoPage(feeAccountUrl);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Record payment")).click();
        page.locator("#amount").fill(amount);
        page.locator("#paymentDate").fill(today().toString());
        page.locator("#paymentMethod").selectOption("CASH");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName("Record payment & open receipt")).click();
        return feeAccountUrl;
    }

    /** Asserts the receipt content in screen mode and returns its number. */
    private String verifyReceiptContent(String studentName) {
        assertTrue(page.getByText("Om Sai Coaching", new Page.GetByTextOptions().setExact(true))
                        .first().isVisible(),
                "The receipt must show the institute brand");
        assertTrue(page.getByText(studentName).first().isVisible(),
                "The receipt must show the student name");
        assertTrue(page.getByText("4000.00").first().isVisible(),
                "The receipt must show the paid amount");
        String receiptNumber = firstReceiptNumberOnPay(page.content());
        assertTrue(Pattern.matches("OMS-RCP-\\d{4}-\\d{6}", receiptNumber),
                "The receipt number must follow the documented pattern, got " + receiptNumber);
        assertTrue(page.getByText("CASH").first().isVisible(),
                "The receipt must show the payment method");
        return receiptNumber;
    }

    /**
     * Opens the receipt again (visiting the freshest ledger entry),
     * saves the screen rendering and the print-media rendering, writes a
     * genuine browser-generated PDF and reports what print shows.
     */
    private ScreenAndPdf receiptPrintRendering(String receiptUrl, String artifactSlug,
                                               String studentName) throws Exception {
        gotoPage(receiptUrl);
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(ARTIFACTS_DIR.resolve(artifactSlug + "-screen.png")
                        .toAbsolutePath().normalize())
                .setFullPage(true));

        page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
        Object noPrintDisplay = page.locator(".no-print").first()
                .evaluate("el => getComputedStyle(el).display");
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(ARTIFACTS_DIR.resolve(artifactSlug + "-print-emulated.png")
                        .toAbsolutePath().normalize())
                .setFullPage(true));

        Path pdfArtifact = ARTIFACTS_DIR.resolve(artifactSlug + "-printed.pdf")
                .toAbsolutePath().normalize();
        page.pdf(new Page.PdfOptions().setPath(pdfArtifact));
        String pdfTextValue = pdfText(pdfArtifact.toString());
        assertTrue(pdfTextValue.contains(studentName),
                "The generated receipt PDF must contain the student name");

        // Back to screen media for the rest of the test.
        page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.SCREEN));

        return new ScreenAndPdf(String.valueOf(noPrintDisplay), pdfTextValue);
    }

    private record ScreenAndPdf(String noPrintDisplay, String pdfText) {}

    /** Extracts text with PDFBox (test dependency; no PDF library in the app). */
    private static String pdfText(String path) throws Exception {
        try (PDDocument document = Loader.loadPDF(new File(path))) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String firstReceiptNumberOnPay(String html) {
        Matcher matcher = Pattern.compile("OMS-RCP-\\d{4}-\\d{6}").matcher(
                new String(html.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        assertTrue(matcher.find(), "No receipt number found on the receipt page");
        return matcher.group();
    }

    /** The &lt;dd&gt; that follows the &lt;dt&gt; labelled {@code label}. */
    private String valueOf(String tag, String label) {
        // Pages use one large <dl> for many pairs, so the dd must be found
        // by its own dt, not by the enclosing dl.
        return page.locator("xpath=//dt[normalize-space(.)='" + label
                + "']/following-sibling::" + tag + "[1]").first().innerText().trim();
    }
}
