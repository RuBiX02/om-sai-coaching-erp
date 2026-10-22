package com.omsai.coaching.web;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.fees.FeeAccount;
import com.omsai.coaching.fees.FeeAccountForm;
import com.omsai.coaching.fees.FeeAccountService;
import com.omsai.coaching.fees.InstallmentService;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The installment screens over HTTP. Administrators may use them,
 * teachers may not - not by URL, not by form submission and not by a
 * guessed id. The authorization is enforced on the server, not by
 * hiding links.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InstallmentAccessWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private FeeAccountService feeAccountService;

    @Autowired
    private InstallmentService installmentService;

    private UserAccount admin;
    private FeeAccount account;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("installment.web.admin");
        Course course = fixtures.course("Installment Web Course");
        Batch batch = fixtures.batch(course, "Morning");
        Enrollment enrollment = fixtures.activeEnrollment(fixtures.student("Installment Web Student"), batch);

        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(enrollment.getId());
        form.setGrossFee(new BigDecimal("20000"));
        form.setAcademicYear("2026-27");
        account = feeAccountService.create(form, admin);
    }

    private static RequestPostProcessor as(UserAccount account) {
        return user(AppUserPrincipal.from(account));
    }

    // ----- administrator ----------------------------------------------------

    @Test
    void theAdministratorReachesEveryInstallmentScreen() throws Exception {
        mockMvc.perform(get("/installments").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/installments/upcoming").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/installments/overdue").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/fees/{id}/installments", account.getId()).with(as(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void thePlanFormShowsTheFeeAgreementItDivides() throws Exception {
        String html = mockMvc.perform(get("/fees/{id}/installments", account.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Installment plan");
        assertThat(html).contains("Installment Web Student");
        assertThat(html).contains("20000");
        assertThat(html).contains("name=\"amount\"");
        assertThat(html).contains("name=\"dueDate\"");
    }

    @Test
    void theAdministratorCreatesAPlanThroughTheForm() throws Exception {
        mockMvc.perform(post("/fees/{id}/installments", account.getId())
                        .with(as(admin)).with(csrf())
                        .param("amount", "10000")
                        .param("dueDate", "2026-11-01")
                        .param("installmentNumber", "1")
                        .param("amount", "10000")
                        .param("dueDate", "2026-12-01")
                        .param("installmentNumber", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/fees/" + account.getId()));

        assertThat(installmentService.viewFor(account.getId())).hasSize(2);
    }

    @Test
    void aScheduleThatDoesNotAddUpIsRejectedWithAMessage() throws Exception {
        String html = mockMvc.perform(post("/fees/{id}/installments", account.getId())
                        .with(as(admin)).with(csrf())
                        .param("amount", "10000")
                        .param("dueDate", "2026-11-01")
                        .param("installmentNumber", "1")
                        .param("amount", "5000")
                        .param("dueDate", "2026-12-01")
                        .param("installmentNumber", "2"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("must add up to exactly the net fee");
        assertThat(installmentService.viewFor(account.getId())).isEmpty();
    }

    @Test
    void aPlanNeedsTheCsrfToken() throws Exception {
        mockMvc.perform(post("/fees/{id}/installments", account.getId())
                        .with(as(admin))
                        .param("amount", "10000")
                        .param("dueDate", "2026-11-01")
                        .param("installmentNumber", "1"))
                .andExpect(status().isForbidden());
    }

    // ----- teacher ----------------------------------------------------------

    @Test
    void teachersAreForbiddenFromEveryInstallmentOperation() throws Exception {
        UserAccount teacher = fixtures.teacherUser("installment.web.teacher");

        mockMvc.perform(get("/installments").with(as(teacher))).andExpect(status().isForbidden());
        mockMvc.perform(get("/installments/upcoming").with(as(teacher))).andExpect(status().isForbidden());
        mockMvc.perform(get("/installments/overdue").with(as(teacher))).andExpect(status().isForbidden());
        mockMvc.perform(get("/fees/{id}/installments", account.getId()).with(as(teacher)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/fees/{id}/installments", account.getId())
                        .with(as(teacher)).with(csrf())
                        .param("amount", "10000")
                        .param("dueDate", "2026-11-01")
                        .param("installmentNumber", "1")
                        .param("amount", "10000")
                        .param("dueDate", "2026-12-01")
                        .param("installmentNumber", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousVisitorsAreRedirectedToTheLoginPage() throws Exception {
        mockMvc.perform(get("/installments")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/fees/{id}/installments", account.getId()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return SecurityMockMvcRequestPostProcessors.csrf();
    }
}
