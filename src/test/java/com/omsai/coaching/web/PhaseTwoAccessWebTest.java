package com.omsai.coaching.web;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.CourseSubject;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.fees.FeeAccount;
import com.omsai.coaching.fees.FeeAccountForm;
import com.omsai.coaching.fees.FeeAccountService;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.support.Fixtures;
import com.omsai.coaching.teacher.Teacher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 access matrix over HTTP. Hiding the links in the UI is not a
 * control: teachers must be turned away from every fee, payment and
 * report page (URL-level rule), and from mutating class sessions they
 * do not own (service-level rule rendered as 403). Requests are made
 * with a real AppUserPrincipal because the controllers resolve the
 * current account through it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PhaseTwoAccessWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private FeeAccountService feeAccountService;

    private Course course;
    private Batch batch;

    private void buildFixture() {
        course = fixtures.course("Access Course");
        batch = fixtures.batch(course, "Morning");
    }

    /** Authenticates requests with the account the controllers must resolve. */
    private static RequestPostProcessor as(UserAccount account) {
        return SecurityMockMvcRequestPostProcessors.user(AppUserPrincipal.from(account));
    }

    // ----- anonymous ---------------------------------------------------------

    @Test
    void anonymousUsersAreRedirectedToLoginEverywhere() throws Exception {
        mockMvc.perform(get("/sessions")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/attendance/reports")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/fees")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/payments")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void postsWithoutCsrfTokenAreRejected() throws Exception {
        buildFixture();
        UserAccount admin = fixtures.adminUser("csrfless");
        mockMvc.perform(post("/sessions").with(as(admin))
                        .param("batchSubjectId", "1")
                        .param("sessionDate", "2026-09-05"))
                .andExpect(status().isForbidden());
    }

    // ----- teacher -------------------------------------------------------------

    @Test
    void teacherCanUseTheClassSessionPages() throws Exception {
        UserAccount teacherUser = fixtures.teacherUser("sessions.teacher");
        fixtures.teacherLinkedTo(teacherUser, "Sessions Teacher");
        mockMvc.perform(get("/sessions").with(as(teacherUser))).andExpect(status().isOk());
        mockMvc.perform(get("/sessions/new").with(as(teacherUser)).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void teacherIsForbiddenFromFeePaymentAndReportPages() throws Exception {
        UserAccount teacherUser = fixtures.teacherUser("forbidden.teacher");
        mockMvc.perform(get("/fees").with(as(teacherUser))).andExpect(status().isForbidden());
        mockMvc.perform(get("/fees/outstanding").with(as(teacherUser))).andExpect(status().isForbidden());
        mockMvc.perform(get("/fees/collections").with(as(teacherUser))).andExpect(status().isForbidden());
        mockMvc.perform(get("/fees/new").queryParam("enrollmentId", "1").with(as(teacherUser)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/payments").with(as(teacherUser))).andExpect(status().isForbidden());
        mockMvc.perform(get("/payments/new").with(as(teacherUser))).andExpect(status().isForbidden());
        mockMvc.perform(get("/attendance/reports").with(as(teacherUser))).andExpect(status().isForbidden());
        mockMvc.perform(get("/attendance/reports/batch").with(as(teacherUser))).andExpect(status().isForbidden());
        mockMvc.perform(get("/attendance/reports/date").with(as(teacherUser))).andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotCreateSessionsForAnAssignmentTheyDoNotOwn() throws Exception {
        buildFixture();
        CourseSubject courseSubject = fixtures.assign(course, fixtures.subject("Access Subject"));
        BatchSubject assignment = fixtures.assignCourseSubject(batch, courseSubject);
        UserAccount outsider = fixtures.teacherUser("outsider.teacher");

        mockMvc.perform(post("/sessions").with(as(outsider)).with(csrf())
                        .param("batchSubjectId", String.valueOf(assignment.getId()))
                        .param("sessionDate", "2026-09-05")
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("topic", "Unauthorized attempt"))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCanCreateASessionForAnAssignmentTheyOwn() throws Exception {
        buildFixture();
        UserAccount teacherUser = fixtures.teacherUser("assignee.teacher");
        Teacher teacher = fixtures.teacherLinkedTo(teacherUser, "Assignee Teacher");
        CourseSubject courseSubject = fixtures.assign(course, fixtures.subject("Owned Subject"));
        BatchSubject assignment = fixtures.assignCourseSubject(batch, courseSubject);
        fixtures.assignTeacher(assignment, teacher);

        mockMvc.perform(post("/sessions").with(as(teacherUser)).with(csrf())
                        .param("batchSubjectId", String.valueOf(assignment.getId()))
                        .param("sessionDate", "2026-09-05")
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().is3xxRedirection());
    }

    // ----- admin ---------------------------------------------------------------

    @Test
    void adminReachesEveryPhaseTwoPage() throws Exception {
        UserAccount admin = fixtures.adminUser("checkpoint.admin");
        // A real fee agreement so the /payments/new page (which needs
        // feeAccountId) can be exercised end to end.
        Course feeCourse = fixtures.course("Fee Page Course");
        Batch feeBatch = fixtures.batch(feeCourse, "Morning");
        Enrollment enrollment = fixtures.activeEnrollment(fixtures.student("Fee Page Student"), feeBatch);
        FeeAccountForm feeForm = new FeeAccountForm();
        feeForm.setEnrollmentId(enrollment.getId());
        feeForm.setGrossFee(new java.math.BigDecimal("12000"));
        FeeAccount account = feeAccountService.create(feeForm, admin);

        mockMvc.perform(get("/sessions").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/sessions/new").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/attendance/reports").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/attendance/reports/batch").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/attendance/reports/subject").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/attendance/reports/date").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/fees").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/fees/outstanding").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/fees/overdue").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/fees/collections").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/payments").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/payments/new").queryParam("feeAccountId", String.valueOf(account.getId()))
                        .with(as(admin))).andExpect(status().isOk());
    }
}
