package com.omsai.coaching.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that the CURATED DEMO CATALOGUE is what the running
 * application actually shows.
 *
 * <p>This test is deliberately different from the rest of the browser
 * suite: it does <em>not</em> boot its own application and it does
 * <em>not</em> use the {@code omsai_browser} database. It drives the
 * application the client actually opens — the one started with the
 * demo profile against the demonstration database — so the evidence is
 * about the real interface and not about a test double.</p>
 *
 * <p>Point it at another instance with {@code DEMO_APP_URL}. The
 * administrator comes from {@code DEMO_ADMIN_EMAIL} and
 * {@code DEMO_ADMIN_PASSWORD}; no credentials are stored in this
 * repository.</p>
 */
@Tag("browser")
class DemoCatalogueVisibleBrowserTest {

    private static final Path ARTIFACTS_DIR = Path.of("target", "browser-artifacts");

    private static Playwright playwright;
    private static Browser browser;

    @BeforeAll
    static void startBrowser() throws Exception {
        // The demonstration application must be running: this test
        // verifies what the client actually sees, so it cannot run
        // against a substitute. When it is not up the test is SKIPPED
        // (not silently passed) and the reason is printed.
        assumeTrue(isDemoApplicationReachable(),
                "The demo application is not running at " + baseUrl()
                        + " - start it with 'zsh scripts/start-demo-app.sh' to run this verification.");
        // The password arrives through the environment - the repository
        // stores no administrator credentials.
        assumeTrue(adminPasswordSet(),
                "Set DEMO_ADMIN_PASSWORD - the password of the demo administrator that "
                        + "scripts/start-demo-app.sh created from APP_ADMIN_PASSWORD.");
        Files.createDirectories(ARTIFACTS_DIR);
        playwright = Playwright.create();
        browser = launchChromium(playwright);
    }

    /** True when the demo application answers on its login page. */
    private static boolean isDemoApplicationReachable() {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                    java.net.URI.create(baseUrl() + "/login").toURL().openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.setRequestMethod("HEAD");
            boolean reachable = connection.getResponseCode() < 500;
            connection.disconnect();
            return reachable;
        } catch (Exception e) {
            return false;
        }
    }

    @AfterAll
    static void stopBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    private static Browser launchChromium(Playwright pw) {
        RuntimeException firstFailure;
        try {
            return pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        } catch (RuntimeException e) {
            firstFailure = e;
        }
        String channel = System.getenv("PW_BROWSER_CHANNEL");
        if (channel != null && !channel.isBlank()) {
            try {
                return pw.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(true).setChannel(channel.trim()));
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
                            .setHeadless(true).setExecutablePath(executable));
                } catch (RuntimeException e) {
                    firstFailure.addSuppressed(e);
                }
            }
        }
        throw firstFailure;
    }

    private static String baseUrl() {
        String url = System.getenv("DEMO_APP_URL");
        return url == null || url.isBlank() ? "http://127.0.0.1:8080" : url.trim();
    }

    private static String adminEmail() {
        String email = System.getenv("DEMO_ADMIN_EMAIL");
        return email == null || email.isBlank() ? "admin@omsai.local" : email.trim();
    }

    /** The demo administrator's password - supplied via the environment only. */
    private static boolean adminPasswordSet() {
        String password = System.getenv("DEMO_ADMIN_PASSWORD");
        return password != null && !password.isBlank();
    }

    private static String adminPassword() {
        String password = System.getenv("DEMO_ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Set DEMO_ADMIN_PASSWORD - the password of the demo administrator. "
                            + "No credentials are hard-coded in this repository.");
        }
        return password;
    }

    /** Signs in through the real rendered login form. */
    private Page signedInPage() {
        Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1280, 900));
        page.navigate(baseUrl() + "/login");
        page.waitForLoadState(LoadState.LOAD);
        page.locator("#username").fill(adminEmail());
        page.locator("#password").fill(adminPassword());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
        page.waitForLoadState(LoadState.LOAD);
        assertTrue(page.url().contains("/dashboard") || page.url().endsWith("/"),
                "The login must reach the application, got " + page.url());
        return page;
    }

    /** Every name shown in the rows of a list page. */
    private List<String> namesOn(Page page, String linkPrefix) {
        List<String> names = page.locator("a[href^='" + linkPrefix + "/']").allInnerTexts();
        List<String> cleaned = new ArrayList<>();
        for (String name : names) {
            String value = name.trim();
            if (!value.isEmpty() && !cleaned.contains(value)) {
                cleaned.add(value);
            }
        }
        return cleaned;
    }

    // ----- the visible pages -------------------------------------------------

    @Test
    void theCourseListShowsTheCuratedOfferings() {
        Page page = signedInPage();
        try {
            page.navigate(baseUrl() + "/courses");
            page.waitForLoadState(LoadState.LOAD);
            List<String> first = namesOn(page, "/courses");
            assertTrue(first.size() >= 20,
                    "The course list must show the catalogue, got: " + first.size() + " rows");

            // The second page holds the rest of the catalogue.
            page.navigate(baseUrl() + "/courses?page=1");
            page.waitForLoadState(LoadState.LOAD);
            List<String> second = namesOn(page, "/courses");
            List<String> all = new ArrayList<>(first);
            for (String name : second) {
                if (!all.contains(name)) {
                    all.add(name);
                }
            }

            for (String expected : List.of(
                    "Standard 7 — Maharashtra State Board Tuition",
                    "Standard 8 — Mathematics and Science Foundation",
                    "Standard 9 — Maharashtra SSC Tuition",
                    "Standard 10 — Maharashtra SSC Board Preparation",
                    "Standard 11 Science — PCM",
                    "Standard 12 Science — PCB",
                    "MHT-CET — PCM Target Batch",
                    "MHT-CET — PCB Target Batch",
                    "JEE Main — PCM Preparation",
                    "JEE Main and Advanced — PCM Preparation",
                    "NEET-UG — Medical Entrance Preparation",
                    "Engineering Mathematics I",
                    "Engineering Mathematics II",
                    "Applied Mathematics — University/Branch-Specific Coaching")) {
                assertTrue(all.contains(expected),
                        "The course list must show \"" + expected + "\", got: " + all);
            }
            assertTrue(all.size() >= 31,
                    "The catalogue has 31 offerings, got: " + all.size() + " -> " + all);

            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(ARTIFACTS_DIR.resolve("demo-catalogue-courses.png")
                            .toAbsolutePath().normalize())
                    .setFullPage(true));
        } finally {
            page.close();
        }
    }

    @Test
    void theSubjectListShowsTheCanonicalSubjects() {
        Page page = signedInPage();
        try {
            page.navigate(baseUrl() + "/subjects");
            page.waitForLoadState(LoadState.LOAD);
            List<String> subjects = namesOn(page, "/subjects");

            assertTrue(subjects.size() >= 18,
                    "The subject list must show the catalogue, got: " + subjects.size());
            for (String expected : List.of(
                    "Mathematics", "Mathematics Part I — Algebra", "Mathematics Part II — Geometry",
                    "General Science", "Science and Technology",
                    "Physics", "Chemistry", "Biology",
                    "English", "Marathi", "Hindi",
                    "History and Civics", "History and Political Science", "Geography",
                    "Computer Studies", "Information Technology",
                    "Engineering Mathematics", "Applied Mathematics")) {
                assertTrue(subjects.contains(expected),
                        "The subject list must show \"" + expected + "\", got: " + subjects);
            }

            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(ARTIFACTS_DIR.resolve("demo-catalogue-subjects.png")
                            .toAbsolutePath().normalize())
                    .setFullPage(true));
        } finally {
            page.close();
        }
    }

    @Test
    void aCourseShowsItsMappedSubjects() {
        Page page = signedInPage();
        try {
            // The PCMB offering of Standard 11: the course detail page
            // must list exactly the subjects of the stream. The list is
            // paginated, so the course is found through the search.
            openCourse(page, "Standard 11 Science — PCMB");

            String assigned = page.locator("ul.list-group").first().innerText();
            for (String expected : List.of("Physics", "Chemistry", "Mathematics", "Biology", "English")) {
                assertTrue(assigned.contains(expected),
                        "The course must map \"" + expected + "\", got: " + assigned);
            }
            assertTrue(!assigned.contains("Applied Mathematics"),
                    "The course must not map a subject it does not teach, got: " + assigned);

            // The MHT-CET PCB offering: physics, chemistry, biology.
            openCourse(page, "MHT-CET — PCB Target Batch");
            String cet = page.locator("ul.list-group").first().innerText();
            assertTrue(cet.contains("Physics") && cet.contains("Chemistry") && cet.contains("Biology"),
                    "The MHT-CET PCB course must map physics, chemistry and biology, got: " + cet);
            assertTrue(!cet.contains("Mathematics"),
                    "The MHT-CET PCB course must not map mathematics, got: " + cet);

            // The JEE offering: physics, chemistry, mathematics.
            openCourse(page, "JEE Main — PCM Preparation");
            String jee = page.locator("ul.list-group").first().innerText();
            assertTrue(jee.contains("Physics") && jee.contains("Chemistry") && jee.contains("Mathematics"),
                    "The JEE course must map physics, chemistry and mathematics, got: " + jee);
            assertTrue(!jee.contains("Biology"),
                    "The JEE course must not map biology, got: " + jee);
        } finally {
            page.close();
        }
    }

    /** Searches a course in the list and opens its detail page. */
    private void openCourse(Page page, String courseName) {
        page.navigate(baseUrl() + "/courses?q=" + java.net.URLEncoder.encode(courseName,
                java.nio.charset.StandardCharsets.UTF_8));
        page.waitForLoadState(LoadState.LOAD);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(courseName)).first().click();
        page.waitForLoadState(LoadState.LOAD);
    }

    @Test
    void theTeacherAndStudentListsShowTheFictionalIdentities() {
        Page page = signedInPage();
        try {
            page.navigate(baseUrl() + "/teachers");
            page.waitForLoadState(LoadState.LOAD);
            List<String> teachers = namesOn(page, "/teachers");
            for (String expected : List.of(
                    "Sandeep Patil", "Neha Kulkarni", "Amit Jadhav", "Pooja Deshmukh",
                    "Rahul Shinde", "Kavita Pawar", "Sneha More", "Imran Shaikh", "Rohan Joshi")) {
                assertTrue(teachers.contains(expected),
                        "The teacher list must show \"" + expected + "\", got: " + teachers);
            }

            page.navigate(baseUrl() + "/students");
            page.waitForLoadState(LoadState.LOAD);
            List<String> students = namesOn(page, "/students");
            for (String expected : List.of(
                    "Ananya Patil", "Vedant Jadhav", "Sai Shinde", "Isha Kulkarni", "Omkar More",
                    "Tanvi Gaikwad", "Atharva Chavan", "Riya Bhosale", "Pranav Pawar",
                    "Aditi Joshi", "Yash Wagh", "Snehal Kadam", "Zoya Shaikh", "Arjun Reddy",
                    "Meera Nair", "Kabir Khan", "Rohan Desai", "Priya Yadav")) {
                assertTrue(students.contains(expected),
                        "The student list must show \"" + expected + "\", got: " + students);
            }
            assertTrue(students.size() >= 18,
                    "The student list must show the 18 demo students, got: " + students.size());
        } finally {
            page.close();
        }
    }

    @Test
    void theOldDebugRecordsAreNotPartOfTheVisibleCatalogue() {
        Page page = signedInPage();
        try {
            for (String path : List.of("/courses", "/subjects", "/teachers", "/students")) {
                page.navigate(baseUrl() + path);
                page.waitForLoadState(LoadState.LOAD);
                String body = page.locator("body").innerText();
                for (String artefact : List.of("DBg Course", "FF Course", "FG Course", "Debug Subject")) {
                    assertTrue(!body.contains(artefact),
                            "The visible catalogue must not contain \"" + artefact + "\" on " + path);
                }
                assertTrue(!body.contains("E2E "),
                        "The visible catalogue must not contain E2E records on " + path);
            }
        } finally {
            page.close();
        }
    }
}
