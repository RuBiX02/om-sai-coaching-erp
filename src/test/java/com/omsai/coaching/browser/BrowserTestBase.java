package com.omsai.coaching.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.CourseSubject;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.subject.Subject;
import com.omsai.coaching.support.Fixtures;
import com.omsai.coaching.teacher.Teacher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared base for real-browser end-to-end tests.
 *
 * <p>These tests boot the actual application (real Tomcat on a random
 * port, real PostgreSQL, Flyway migrations) under the {@code browser}
 * profile, which points at a dedicated disposable database
 * ({@code omsai_browser}) and a temporary administrator account. A
 * Chromium-family browser drives the rendered UI over real HTTP; no
 * MockMvc and no curl are involved.</p>
 *
 * <p>Infrastructure notes:</p>
 * <ul>
 *   <li>The Spring context and the Playwright {@link Browser} are shared
 *       across all classes in the suite; every test gets a fresh, isolated
 *       browser context (cookies are never reused between tests).</li>
 *   <li>Console messages, failed requests and HTTP 4xx/5xx responses are
 *       recorded for every page; {@link #assertNoClientOrServerErrors()}
 *       asserts none of them occurred so far.</li>
 *   <li>On failure a full-page screenshot plus the diagnostic log of the
 *       test is written to {@code target/browser-artifacts}.</li>
 *   <li>Every seeded row is tracked and deleted again in
 *       {@link #cleanupOwnData()} — only rows the suite itself created are
 *       removed, and always from the disposable browser database.</li>
 * </ul>
 */
@Tag("browser")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("browser")
public abstract class BrowserTestBase {

    /** Temporary administrator created by the browser profile bootstrap. */
    public static final String ADMIN_EMAIL = "browser-admin@omsai.test";
    public static final String ADMIN_PASSWORD = "BrowserAdmin#2026#Pass";

    public static final Path ARTIFACTS_DIR = Path.of("target", "browser-artifacts");

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");

    /* Shared Playwright machinery (one per JVM for the whole suite). */
    private static Playwright playwright;
    private static Browser sharedBrowser;

    /**
     * Captures failure diagnostics (screenshot + console/HTTP log of the
     * failing page) while the browser context is still open. The
     * class-level {@code TestWatcher} callbacks turned out not to be
     * invoked under this stack (JUnit Jupiter 6 + Spring), so the
     * interception handles it directly.
     */
    @RegisterExtension
    final FailureDiagnostics failureDiagnostics = new FailureDiagnostics();

    @Autowired
    protected Fixtures fixtures;
    @Autowired
    protected JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    protected String baseUrl;
    protected BrowserContext context;
    protected Page page;

    /* Rows created for the running test, deleted again in cleanup. */
    protected final List<Long> trackedUserIds = new ArrayList<>();
    protected final List<Long> trackedTeacherIds = new ArrayList<>();
    protected final List<Long> trackedStudentIds = new ArrayList<>();
    protected final List<Long> trackedCourseIds = new ArrayList<>();
    protected final List<Long> trackedSubjectIds = new ArrayList<>();
    protected final List<Long> trackedCourseSubjectIds = new ArrayList<>();
    protected final List<Long> trackedBatchIds = new ArrayList<>();
    protected final List<Long> trackedBatchSubjectIds = new ArrayList<>();
    protected final List<Long> trackedEnrollmentIds = new ArrayList<>();

    /* Diagnostics captured for the running test. */
    protected final List<String> consoleLog = new ArrayList<>();
    protected final List<String> serverErrors = new ArrayList<>();
    protected final List<String> requestFailures = new ArrayList<>();

    @BeforeAll
    void bootSuite() throws Exception {
        baseUrl = "http://localhost:" + port;
        Files.createDirectories(ARTIFACTS_DIR);
        requireDisposableDatabase();
        sharedBrowser();
    }

    /**
     * Hard safety guard: this suite may only ever run against the
     * disposable browser database. The check queries the live connection
     * (not the configured URL), so an accidental override of
     * {@code BROWSER_TEST_DB_URL} cannot redirect the tests at the
     * "omsai" development database or at "omsai_test".
     */
    private void requireDisposableDatabase() {
        String database = jdbc.queryForObject("select current_database()", String.class);
        if (database == null || !database.startsWith("omsai_browser")) {
            throw new IllegalStateException("Browser tests refuse to run against database '"
                    + database + "'; only the disposable 'omsai_browser*' database is allowed.");
        }
    }

    @BeforeEach
    void freshBrowserContext() {
        closeStaleContext();
        context = sharedBrowser().newContext(
                new Browser.NewContextOptions().setViewportSize(1280, 900));
        page = context.newPage();

        consoleLog.clear();
        serverErrors.clear();
        requestFailures.clear();
        page.onConsoleMessage(this::recordConsole);
        page.onResponse(response -> {
            if (response.status() >= 400) {
                serverErrors.add(response.status() + " " + response.url());
            }
        });
        page.onRequestFailed(request ->
                requestFailures.add(request.method() + " " + request.url()));
        // The app uses window.confirm() for destructive actions; tests
        // always proceed with the dialogue.
        page.onDialog(dialog -> {
            consoleLog.add("dialog '" + dialog.message() + "' accepted on " + dialog.page().url());
            dialog.accept();
        });
    }

    @AfterEach
    void cleanupOwnData() {
        try {
            deleteOwnData();
        } finally {
            trackedUserIds.clear();
            trackedTeacherIds.clear();
            trackedStudentIds.clear();
            trackedCourseIds.clear();
            trackedSubjectIds.clear();
            trackedCourseSubjectIds.clear();
            trackedBatchIds.clear();
            trackedBatchSubjectIds.clear();
            trackedEnrollmentIds.clear();
        }
    }

    /* ----- Failure diagnostics via InvocationInterceptor --------------------- */

    /**
     * Registered extension that wraps each test-method invocation and
     * captures the diagnostic artifacts of a failing test while the
     * browser context is still open (page not closed, cookies intact).
     */
    final class FailureDiagnostics implements InvocationInterceptor {

        @Override
        public void interceptTestMethod(Invocation<Void> invocation,
                                        ReflectiveInvocationContext<Method> invocationContext,
                                        ExtensionContext extensionContext) throws Throwable {
            try {
                invocation.proceed();
            } catch (Throwable failure) {
                captureFailureArtifacts(extensionContext, failure);
                throw failure;
            }
        }
    }

    private void captureFailureArtifacts(ExtensionContext extensionContext, Throwable cause) {
        Page failingPage = page;
        if (failingPage == null) {
            return; // context never opened (e.g. infrastructure failure)
        }
        try {
            String className = extensionContext.getTestClass()
                    .map(Class::getSimpleName).orElse("Unknown");
            String methodName = extensionContext.getDisplayName().replaceAll("[^A-Za-z0-9]+", "_");
            String slug = className + "-" + methodName + "-failure";
            failingPage.screenshot(new Page.ScreenshotOptions()
                    .setPath(ARTIFACTS_DIR.resolve(slug + ".png")
                            .toAbsolutePath().normalize())
                    .setFullPage(true));
            List<String> lines = new ArrayList<>();
            lines.add("URL:          " + failingPage.url());
            lines.add("Failure:      " + cause);
            lines.add("--- console messages / dialogs ---");
            consoleLog.forEach(lines::add);
            lines.add("--- HTTP responses >= 400 ---");
            serverErrors.forEach(lines::add);
            lines.add("--- failed HTTP requests ---");
            requestFailures.forEach(lines::add);
            Files.write(ARTIFACTS_DIR.resolve(slug + ".log"), lines);
        } catch (Exception e) {
            // Diagnostics must never mask the original assertion failure.
            System.err.println("Could not write browser failure artifacts: " + e);
        }
    }

    /* ----- Small convenience helpers ---------------------------------------- */

    /** Today in the application's business timezone. */
    protected LocalDate today() {
        return LocalDate.now(APP_ZONE);
    }

    /** Navigate to a path (or absolute URL) of the running app and wait for load. */
    protected void gotoPage(String pathOrUrl) {
        page.navigate(pathOrUrl.startsWith("http") ? pathOrUrl : baseUrl + pathOrUrl);
        page.waitForLoadState(LoadState.LOAD);
    }

    /** Sign in through the rendered login form. */
    protected void login(String email, String password) {
        gotoPage("/login");
        page.locator("#username").fill(email);
        page.locator("#password").fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
        page.waitForLoadState(LoadState.LOAD);
    }

    /** Sign in as the bootstrapped administrator and land on /dashboard. */
    protected void loginAdmin() {
        login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertTrue(page.url().endsWith("/dashboard"),
                "Admin login expected the dashboard, but landed on " + page.url());
    }

    /** Sign in as a teacher and land on the teacher home page. */
    protected void loginTeacher(UserAccount teacherUser) {
        login(teacherUser.getEmail(), fixtures.safePassword());
        assertTrue(page.url().endsWith("/teacher"),
                "Teacher login expected /teacher, but landed on " + page.url());
    }

    /** Sign out through the rendered topbar button. */
    protected void logout() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign out")).click();
        page.waitForLoadState(LoadState.LOAD);
    }

    /** Assert the current page shows a flash alert containing the fragment. */
    protected void assertFlash(String expectedFragment) {
        assertTrue(page.locator(".app-alerts",
                        new Page.LocatorOptions().setHasText(expectedFragment)).first().isVisible(),
                "Expected a flash message containing '" + expectedFragment + "' on " + page.url());
    }

    /** Fail when the browser saw console errors, failed requests or 4xx/5xx. */
    protected void assertNoClientOrServerErrors() {
        assertNoClientOrServerErrors(new int[0]);
    }
    /**
     * Fail when the browser saw console errors, failed requests or
     * unexpected 4xx/5xx responses. Statuses listed in {@code allowed}
     * are expected by the test (e.g. 403 when asserting that teachers
     * cannot open fee pages) and are excluded.
     */
    protected void assertNoClientOrServerErrors(int... allowed) {
        java.util.Set<Integer> allowedSet = java.util.Arrays.stream(allowed)
                .boxed().collect(java.util.stream.Collectors.toSet());
        List<String> problems = new ArrayList<>();
        consoleLog.stream()
                .filter(line -> line.startsWith("error"))
                .filter(line -> !isAllowedResourceError(line, allowedSet))
                .forEach(line -> problems.add("console: " + line));
        requestFailures.forEach(line -> problems.add("request: " + line));
        serverErrors.stream()
                .filter(line -> !allowedSet.contains(statusOf(line)))
                .forEach(line -> problems.add("http: " + line));
        assertTrue(problems.isEmpty(),
                "Unexpected browser errors on " + page.url() + ":\n  "
                        + String.join("\n  ", problems));
    }

    /** True for console "Failed to load resource: … 403 ()" lines of an allowed status. */
    private static boolean isAllowedResourceError(String consoleLine, java.util.Set<Integer> allowed) {
        if (!consoleLine.contains("Failed to load resource")) {
            return false;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("status of (\\d{3})").matcher(consoleLine);
        return matcher.find() && allowed.contains(Integer.parseInt(matcher.group(1)));
    }

    private static int statusOf(String recordedResponse) {
        try {
            return Integer.parseInt(recordedResponse.substring(0, recordedResponse.indexOf(' ')));
        } catch (RuntimeException e) {
            return -1; // never allowed
        }
    }

    /* ----- Seeding (through the shared fixture component) -------------------- */

    /** A minimal course → subject → course-subject → batch → batch-subject tree. */
    protected record BaseTree(Course course, Subject subject, CourseSubject courseSubject,
                              Batch batch, BatchSubject batchSubject) {}

    protected BaseTree seedBaseTree(String prefix) {
        Course course = fixtures.course(prefix + " Course");
        Subject subject = fixtures.subject(prefix + " Subject");
        CourseSubject courseSubject = fixtures.assign(course, subject);
        Batch batch = fixtures.batch(course, prefix + " Batch");
        BatchSubject batchSubject = fixtures.assignCourseSubject(batch, courseSubject);
        trackedCourseIds.add(course.getId());
        trackedSubjectIds.add(subject.getId());
        trackedCourseSubjectIds.add(courseSubject.getId());
        trackedBatchIds.add(batch.getId());
        trackedBatchSubjectIds.add(batchSubject.getId());
        return new BaseTree(course, subject, courseSubject, batch, batchSubject);
    }

    protected BatchSubject trackedBatchSubject(Batch batch, CourseSubject courseSubject) {
        BatchSubject batchSubject = fixtures.assignCourseSubject(batch, courseSubject);
        trackedBatchSubjectIds.add(batchSubject.getId());
        return batchSubject;
    }

    protected Student trackedStudent(String fullName) {
        Student student = fixtures.student(fullName);
        trackedStudentIds.add(student.getId());
        return student;
    }

    protected Enrollment trackedEnrollment(Student student, Batch batch) {
        Enrollment enrollment = fixtures.activeEnrollment(student, batch);
        trackedEnrollmentIds.add(enrollment.getId());
        return enrollment;
    }

    protected UserAccount trackedTeacherUser(String emailPrefix) {
        UserAccount user = fixtures.teacherUser(emailPrefix);
        trackedUserIds.add(user.getId());
        return user;
    }

    protected Teacher trackedTeacherProfile(UserAccount user, String fullName) {
        Teacher teacher = fixtures.teacherLinkedTo(user, fullName);
        trackedTeacherIds.add(teacher.getId());
        return teacher;
    }

    /* ----- Cleanup: delete exactly the rows this test created ---------------- */

    private void deleteOwnData() {
        // Chosen to satisfy every FK (all are ON DELETE RESTRICT):
        // attendance → class_sessions → payments → fee_accounts →
        // enrollments → batch_subjects → teachers → user_accounts →
        // course_subjects → students → batches → subjects → courses.
        if (!trackedBatchSubjectIds.isEmpty()) {
            jdbc.batchUpdate("DELETE FROM attendance WHERE class_session_id IN "
                    + "(SELECT id FROM class_sessions WHERE batch_subject_id = ?)",
                    asParams(trackedBatchSubjectIds));
            jdbc.batchUpdate("DELETE FROM class_sessions WHERE batch_subject_id = ?",
                    asParams(trackedBatchSubjectIds));
        }
        for (Long id : trackedStudentIds) {
            jdbc.update("DELETE FROM payments WHERE fee_account_id IN "
                    + "(SELECT id FROM fee_accounts WHERE student_id = ?)", id);
            jdbc.update("DELETE FROM fee_accounts WHERE student_id = ?", id);
            jdbc.update("DELETE FROM enrollments WHERE student_id = ?", id);
        }
        for (Long id : trackedEnrollmentIds) {
            jdbc.update("DELETE FROM payments WHERE fee_account_id IN "
                    + "(SELECT id FROM fee_accounts WHERE enrollment_id = ?)", id);
            jdbc.update("DELETE FROM fee_accounts WHERE enrollment_id = ?", id);
            jdbc.update("DELETE FROM enrollments WHERE id = ?", id);
        }
        for (Long id : trackedCourseIds) {
            jdbc.update("DELETE FROM payments WHERE fee_account_id IN "
                    + "(SELECT id FROM fee_accounts WHERE course_id = ?)", id);
            jdbc.update("DELETE FROM fee_accounts WHERE course_id = ?", id);
        }
        for (Long id : trackedBatchIds) {
            jdbc.update("DELETE FROM enrollments WHERE batch_id = ?", id);
            jdbc.update("DELETE FROM batch_subjects WHERE batch_id = ?", id);
        }
        for (Long id : trackedTeacherIds) {
            jdbc.update("DELETE FROM teachers WHERE id = ?", id);
        }
        for (Long id : trackedUserIds) {
            jdbc.update("DELETE FROM user_accounts WHERE id = ?", id);
        }
        for (Long id : trackedCourseSubjectIds) {
            jdbc.update("DELETE FROM course_subjects WHERE id = ?", id);
        }
        for (Long id : trackedStudentIds) {
            jdbc.update("DELETE FROM students WHERE id = ?", id);
        }
        for (Long id : trackedBatchIds) {
            jdbc.update("DELETE FROM batches WHERE id = ?", id);
        }
        for (Long id : trackedSubjectIds) {
            jdbc.update("DELETE FROM subjects WHERE id = ?", id);
        }
        for (Long id : trackedCourseIds) {
            jdbc.update("DELETE FROM courses WHERE id = ?", id);
        }
    }

    private static List<Object[]> asParams(List<Long> ids) {
        return ids.stream().map(id -> new Object[]{id}).toList();
    }

    /* ----- Playwright lifecycle ---------------------------------------------- */

    private static Browser sharedBrowser() {
        if (sharedBrowser == null) {
            synchronized (BrowserTestBase.class) {
                if (sharedBrowser == null) {
                    playwright = Playwright.create();
                    sharedBrowser = launchBestEffortChromium(playwright);
                    // Runs once per JVM; close everything at exit.
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            sharedBrowser.close();
                            playwright.close();
                        } catch (RuntimeException ignored) {
                            // already gone
                        }
                    }));
                }
            }
        }
        return sharedBrowser;
    }

    /**
     * Launch an available Chromium-family browser, trying in order:
     * (1) the Playwright-managed binary (CI installs it explicitly),
     * (2) the PW_BROWSER_CHANNEL channel (e.g. system Google Chrome),
     * (3) well-known system Chromium/Chrome executables.
     */
    private static Browser launchBestEffortChromium(Playwright pw) {
        RuntimeException firstFailure;
        try {
            return pw.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
        } catch (RuntimeException e) {
            firstFailure = e;
        }
        String channel = System.getenv("PW_BROWSER_CHANNEL");
        if (channel != null && !channel.isBlank()) {
            try {
                return pw.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setChannel(channel.trim()));
            } catch (RuntimeException e) {
                firstFailure.addSuppressed(e);
            }
        }
        for (String candidate : List.of(
                "/usr/bin/chromium", "/usr/bin/chromium-browser",
                "/usr/bin/google-chrome", "/usr/bin/google-chrome-stable")) {
            Path executable = Path.of(candidate);
            if (Files.isExecutable(executable)) {
                try {
                    return pw.chromium().launch(new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setExecutablePath(executable));
                } catch (RuntimeException e) {
                    firstFailure.addSuppressed(e);
                }
            }
        }
        throw firstFailure;
    }

    private void closeStaleContext() {
        if (context != null) {
            try {
                context.close();
            } catch (RuntimeException ignored) {
                // already dead — keep going
            }
            context = null;
            page = null;
        }
    }

    private void recordConsole(ConsoleMessage message) {
        consoleLog.add(message.type() + ": " + message.text()
                + " (" + message.location() + ")");
    }
}
