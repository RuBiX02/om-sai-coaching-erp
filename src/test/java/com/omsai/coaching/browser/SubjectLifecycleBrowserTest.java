package com.omsai.coaching.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.omsai.coaching.course.CourseRepository;
import com.omsai.coaching.subject.Subject;
import com.omsai.coaching.subject.SubjectForm;
import com.omsai.coaching.subject.SubjectService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-browser verification of the subject lifecycle and of the curated
 * catalogue:
 *
 * <ul>
 *   <li>an administrator sees the catalogue of the demo dataset in the
 *       ordinary course and subject screens;</li>
 *   <li>an administrator can deactivate a subject and activate it again;</li>
 *   <li>a deactivated subject is no longer offered when a subject is
 *       assigned to a course, while the courses that already include it
 *       keep showing it.</li>
 * </ul>
 */
@Tag("browser")
class SubjectLifecycleBrowserTest extends BrowserTestBase {

    @Autowired
    private SubjectService subjectService;

    @Autowired
    private CourseRepository courseRepository;

    @Test
    void theAdministratorSeesTheCuratedCatalogue() {
        loginAdmin();

        gotoPage("/courses");
        String list = page.locator(".card").first().innerText().replaceAll("\\s+", " ");
        assertTrue(list.contains("MHT-CET"),
                "The course list must show the curated offerings, got: " + list);
        assertTrue(list.contains("JEE"), "The course list must show the JEE offerings");
        assertTrue(list.contains("NEET"), "The course list must show the NEET offerings");
        assertTrue(list.contains("Standard 11"),
                "The course list must show the senior secondary offerings");

        gotoPage("/subjects");
        String subjects = page.locator(".card").first().innerText().replaceAll("\\s+", " ");
        assertTrue(subjects.contains("Physics"), "The subject list must show the catalogue subjects");
        assertTrue(subjects.contains("Mathematics"), "The subject list must show the catalogue subjects");
    }

    @Test
    void aCourseShowsItsOwnSubjects() {
        loginAdmin();

        // The PCMB offering of Standard 11.
        Long courseId = courseIdOf("Standard 11 Science — PCMB");

        gotoPage("/courses/" + courseId);
        // The subjects the course actually teaches (the list on the
        // left, not the picker of subjects that could be added).
        String assigned = page.locator("ul.list-group").first().innerText().replaceAll("\\s+", " ");
        assertTrue(assigned.contains("Physics"), "The course must list Physics, got: " + assigned);
        assertTrue(assigned.contains("Chemistry"), "The course must list Chemistry, got: " + assigned);
        assertTrue(assigned.contains("Biology"), "The course must list Biology, got: " + assigned);
        assertTrue(assigned.contains("Mathematics"), "The course must list Mathematics, got: " + assigned);
        assertTrue(assigned.contains("English"), "The course must list English, got: " + assigned);
        // A PCMB course does not teach Applied Mathematics.
        assertTrue(!assigned.contains("Applied Mathematics"),
                "The course must not list a subject it does not teach, got: " + assigned);
    }

    @Test
    void theAdministratorDeactivatesAndReactivatesASubject() {
        loginAdmin();
        Subject subject = fixtures.subject("Browser Lifecycle Subject");
        trackedSubjectIds.add(subject.getId());

        gotoPage("/subjects/" + subject.getId());
        assertTrue(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                        .setName("Deactivate")).first().isVisible(),
                "An unused subject must offer a Deactivate action");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Deactivate")).first().click();
        page.waitForLoadState(LoadState.LOAD);
        assertFlash("Subject deactivated");

        assertTrue(page.getByText("INACTIVE").first().isVisible(),
                "The deactivated subject must be marked as inactive");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Activate")).first().click();
        page.waitForLoadState(LoadState.LOAD);
        assertFlash("Subject activated again");

        assertNoClientOrServerErrors();
    }

    @Test
    void aDeactivatedSubjectIsNotOfferedForANewCourseAssignment() {
        loginAdmin();
        var tree = seedBaseTree("BrowserPicker");
        // A subject that is not part of the course yet.
        Subject spare = fixtures.subject("Browser Spare Subject");
        trackedSubjectIds.add(spare.getId());

        // Deactivate it through the service (the UI path is covered by
        // the test above).
        subjectService.setActive(spare.getId(), false);

        gotoPage("/courses/" + tree.course().getId());
        String detail = page.locator(".app-main").innerText().replaceAll("\\s+", " ");

        // The subject that is already assigned is still listed.
        assertTrue(detail.contains(tree.subject().getName()),
                "The course must keep showing the subject it already includes");
        // The deactivated one is not offered any more.
        String options = page.locator("select[name='subjectId']").innerText();
        assertTrue(!options.contains(spare.getName()),
                "A deactivated subject must not be offered for a new course assignment, got: " + options);
        assertTrue(options.contains(tree.subject().getName()) == false,
                "The assigned subject must not be offered again either");

        assertNoClientOrServerErrors();
    }

    @Test
    void aDeactivatedSubjectKeepsItsExistingCourseAssignment() {
        loginAdmin();
        var tree = seedBaseTree("BrowserKept");
        subjectService.setActive(tree.subject().getId(), false);

        gotoPage("/courses/" + tree.course().getId());
        String detail = page.locator(".app-main").innerText().replaceAll("\\s+", " ");

        assertTrue(detail.contains(tree.subject().getName()),
                "A deactivated subject must keep its existing course assignment");
        assertTrue(detail.contains("Deactivated"),
                "The course must show that the subject is deactivated");

        assertNoClientOrServerErrors();
    }

    /** The id of a course of the curated catalogue. */
    private Long courseIdOf(String name) {
        return courseRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new IllegalStateException("missing demo course: " + name))
                .getId();
    }
}
