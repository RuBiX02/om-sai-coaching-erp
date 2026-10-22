package com.omsai.coaching.web;

import com.omsai.coaching.course.Course;
import com.omsai.coaching.course.CourseForm;
import com.omsai.coaching.course.CourseService;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.subject.Subject;
import com.omsai.coaching.subject.SubjectForm;
import com.omsai.coaching.subject.SubjectService;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The subject lifecycle over HTTP: an administrator may delete an
 * unused subject and deactivate a used one; teachers, students and
 * anonymous visitors may not touch either action.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SubjectLifecycleAccessWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private SubjectService subjectService;

    @Autowired
    private CourseService courseService;

    private UserAccount admin;
    private Subject unused;
    private Subject used;
    private Course course;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("subject.web.admin");
        unused = createSubject("Unused Web Subject");
        used = createSubject("Used Web Subject");
        course = fixtures.course("Subject Web Course");
        fixtures.assign(course, used);
    }

    private Subject createSubject(String name) {
        SubjectForm form = new SubjectForm();
        form.setName(name + " " + System.nanoTime());
        return subjectService.create(form);
    }

    private static RequestPostProcessor as(UserAccount account) {
        return user(AppUserPrincipal.from(account));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return SecurityMockMvcRequestPostProcessors.csrf();
    }

    // ----- administrator ----------------------------------------------------

    @Test
    void theAdministratorDeletesAnUnusedSubject() throws Exception {
        mockMvc.perform(post("/subjects/{id}/delete", unused.getId()).with(as(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/subjects"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void theDeletionOfAUsedSubjectIsRefusedWithAnExplanation() throws Exception {
        mockMvc.perform(post("/subjects/{id}/delete", used.getId()).with(as(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/subjects/" + used.getId()))
                .andExpect(flash().attributeExists("errorMessage"));

        // Still there.
        assertThat(subjectService.get(used.getId())).isNotNull();
    }

    @Test
    void theAdministratorDeactivatesAndReactivatesASubject() throws Exception {
        mockMvc.perform(post("/subjects/{id}/status", used.getId()).with(as(admin)).with(csrf())
                        .param("active", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/subjects/" + used.getId()))
                .andExpect(flash().attributeExists("successMessage"));
        assertThat(subjectService.isActive(used.getId())).isFalse();

        mockMvc.perform(post("/subjects/{id}/status", used.getId()).with(as(admin)).with(csrf())
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
        assertThat(subjectService.isActive(used.getId())).isTrue();
    }

    @Test
    void anInvalidTransitionIsRefusedWithAMessage() throws Exception {
        mockMvc.perform(post("/subjects/{id}/status", used.getId()).with(as(admin)).with(csrf())
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void theSubjectPageOffersTheRightAction() throws Exception {
        // An unused subject may be deleted and may also be deactivated.
        String unusedHtml = mockMvc.perform(get("/subjects/{id}", unused.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(unusedHtml).contains("Delete subject");
        assertThat(unusedHtml).contains("Deactivate");

        // A subject that is already taught keeps its history: it can be
        // deactivated but not deleted.
        String usedHtml = mockMvc.perform(get("/subjects/{id}", used.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(usedHtml).contains("Deactivate");
        assertThat(usedHtml).doesNotContain("Delete subject");
        assertThat(usedHtml).contains("cannot be deleted");
    }

    @Test
    void theSubjectPageKeepsShowingTheExistingCourses() throws Exception {
        String html = mockMvc.perform(get("/subjects/{id}", used.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(course.getName());
    }

    @Test
    void anInactiveSubjectIsNotOfferedOnTheCoursePage() throws Exception {
        mockMvc.perform(post("/subjects/{id}/status", used.getId()).with(as(admin)).with(csrf())
                .param("active", "false"));

        String html = mockMvc.perform(get("/courses/{id}", course.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The subject is still listed as part of the course...
        assertThat(html).contains(used.getName());
        // ...but it is not offered for a new course.
        assertThat(html).doesNotContain("option value=\"" + used.getId() + "\"");
    }

    @Test
    void theLifecycleActionsNeedTheCsrfToken() throws Exception {
        mockMvc.perform(post("/subjects/{id}/status", used.getId()).with(as(admin))
                        .param("active", "false"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/subjects/{id}/delete", unused.getId()).with(as(admin)))
                .andExpect(status().isForbidden());
    }

    // ----- teacher ----------------------------------------------------------

    @Test
    void teachersCannotUseTheLifecycleActions() throws Exception {
        UserAccount teacher = fixtures.teacherUser("subject.web.teacher");

        mockMvc.perform(get("/subjects").with(as(teacher))).andExpect(status().isForbidden());
        mockMvc.perform(get("/subjects/{id}", unused.getId()).with(as(teacher)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/subjects/{id}/delete", unused.getId())
                        .with(as(teacher)).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/subjects/{id}/status", used.getId())
                        .with(as(teacher)).with(csrf())
                        .param("active", "false"))
                .andExpect(status().isForbidden());
    }

    // ----- anonymous ---------------------------------------------------------

    @Test
    void anonymousVisitorsAreRedirectedToTheLoginPage() throws Exception {
        mockMvc.perform(get("/subjects/{id}", unused.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/subjects/{id}/delete", unused.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/subjects/{id}/status", unused.getId()).with(csrf())
                        .param("active", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
