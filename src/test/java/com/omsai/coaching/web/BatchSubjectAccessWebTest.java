package com.omsai.coaching.web;

import com.omsai.coaching.attendance.ClassSessionRepository;
import com.omsai.coaching.attendance.ClassSessionService;
import com.omsai.coaching.attendance.SessionForm;
import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.CourseSubject;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
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

import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level checks for the batch-subject lifecycle.
 *
 * <p>Two things matter here: a blocked removal must come back as a
 * normal redirect with a readable explanation instead of an error page
 * with a database message, and the lifecycle actions must stay behind
 * the administrator role.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BatchSubjectAccessWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private ClassSessionService sessionService;

    @Autowired
    private ClassSessionRepository sessionRepository;

    private UserAccount admin;
    private Course course;
    private Batch batch;
    private BatchSubject assignment;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("subjectlifecycle.admin");
        course = fixtures.course("Subject Lifecycle Course");
        batch = fixtures.batch(course, "Morning");
        CourseSubject courseSubject = fixtures.assign(course, fixtures.subject("Subject Lifecycle Subject"));
        assignment = fixtures.assignCourseSubject(batch, courseSubject);
    }

    private static RequestPostProcessor as(UserAccount account) {
        return user(AppUserPrincipal.from(account));
    }

    // ----- the happy paths --------------------------------------------------

    @Test
    void adminCanDeactivateAnAssignment() throws Exception {
        mockMvc.perform(post("/batches/subjects/{id}/status", assignment.getId())
                        .with(as(admin)).with(csrf())
                        .param("active", "false")
                        .param("batchId", String.valueOf(batch.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/batches/" + batch.getId()))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void adminCanReactivateAnAssignment() throws Exception {
        mockMvc.perform(post("/batches/subjects/{id}/status", assignment.getId())
                        .with(as(admin)).with(csrf())
                        .param("active", "false")
                        .param("batchId", String.valueOf(batch.getId())))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/batches/subjects/{id}/status", assignment.getId())
                        .with(as(admin)).with(csrf())
                        .param("active", "true")
                        .param("batchId", String.valueOf(batch.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void adminCanRemoveAnAssignmentThatWasNeverUsed() throws Exception {
        mockMvc.perform(post("/batches/subjects/{id}/remove", assignment.getId())
                        .with(as(admin)).with(csrf())
                        .param("batchId", String.valueOf(batch.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/batches/" + batch.getId()))
                .andExpect(flash().attributeExists("successMessage"));
    }

    // ----- the blocked paths -------------------------------------------------

    @Test
    void removingAnAssignmentThatHasClassesIsRefusedWithAnExplanation() throws Exception {
        createClass();

        mockMvc.perform(post("/batches/subjects/{id}/remove", assignment.getId())
                        .with(as(admin)).with(csrf())
                        .param("batchId", String.valueOf(batch.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/batches/" + batch.getId()))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void aRefusedRemovalDoesNotDeleteAnything() throws Exception {
        createClass();

        mockMvc.perform(post("/batches/subjects/{id}/remove", assignment.getId())
                        .with(as(admin)).with(csrf())
                        .param("batchId", String.valueOf(batch.getId())))
                .andExpect(status().is3xxRedirection());

        assert sessionRepository.countByBatchSubjectId(assignment.getId()) == 1
                : "the class of the assignment must still exist";
    }

    @Test
    void deactivatingTwiceIsRefusedWithAnExplanation() throws Exception {
        mockMvc.perform(post("/batches/subjects/{id}/status", assignment.getId())
                        .with(as(admin)).with(csrf())
                        .param("active", "false")
                        .param("batchId", String.valueOf(batch.getId())))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/batches/subjects/{id}/status", assignment.getId())
                        .with(as(admin)).with(csrf())
                        .param("active", "false")
                        .param("batchId", String.valueOf(batch.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // ----- authorization -----------------------------------------------------

    @Test
    void teacherCannotReachTheBatchPages() throws Exception {
        UserAccount teacherUser = fixtures.teacherUser("batch.teacher");
        fixtures.teacherLinkedTo(teacherUser, "Batch Teacher");

        mockMvc.perform(get("/batches").with(as(teacherUser))).andExpect(status().isForbidden());
        mockMvc.perform(get("/batches/{id}", batch.getId()).with(as(teacherUser)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/batches/subjects/{id}/status", assignment.getId())
                        .with(as(teacherUser)).with(csrf())
                        .param("active", "false")
                        .param("batchId", String.valueOf(batch.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/batches/subjects/{id}/remove", assignment.getId())
                        .with(as(teacherUser)).with(csrf())
                        .param("batchId", String.valueOf(batch.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUsersAreRedirectedToLogin() throws Exception {
        mockMvc.perform(post("/batches/subjects/{id}/status", assignment.getId())
                        .with(csrf())
                        .param("active", "false")
                        .param("batchId", String.valueOf(batch.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ----- the page itself ----------------------------------------------------

    @Test
    void theBatchPageStillRendersWithALifecycleAction() throws Exception {
        createClass();

        mockMvc.perform(get("/batches/{id}", batch.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("Deactivate")));
    }

    // ----- helpers -------------------------------------------------------------

    private void createClass() {
        SessionForm form = new SessionForm();
        form.setBatchSubjectId(assignment.getId());
        form.setSessionDate(LocalDate.of(2026, 9, 7));
        form.setStartTime(LocalTime.of(10, 0));
        form.setEndTime(LocalTime.of(11, 0));
        sessionService.create(form, admin);
    }
}
