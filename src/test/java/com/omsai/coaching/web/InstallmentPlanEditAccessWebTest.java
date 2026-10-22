package com.omsai.coaching.web;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.fees.FeeAccount;
import com.omsai.coaching.fees.FeeAccountForm;
import com.omsai.coaching.fees.FeeAccountService;
import com.omsai.coaching.fees.InstallmentInput;
import com.omsai.coaching.fees.InstallmentService;
import com.omsai.coaching.fees.PaymentForm;
import com.omsai.coaching.fees.PaymentService;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserForm;
import com.omsai.coaching.security.UserService;
import com.omsai.coaching.student.Student;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Editing an installment plan over HTTP.
 *
 * <p>An administrator may open the edit form of a plan that carries no
 * payment-allocation history and replace the schedule. When the history
 * exists the plan is immutable: the form is not offered and a forged
 * submission is refused. Teachers and students are turned away by the
 * server - not by a hidden link - on direct URLs, on forged POSTs and
 * on guessed fee-account ids.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InstallmentPlanEditAccessWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private FeeAccountService feeAccountService;

    @Autowired
    private InstallmentService installmentService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserService userService;

    private UserAccount admin;
    private FeeAccount account;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("planedit.web.admin");
        Course course = fixtures.course("Plan Edit Web Course");
        Batch batch = fixtures.batch(course, "Morning");
        Enrollment enrollment = fixtures.activeEnrollment(fixtures.student("Plan Edit Web Student"), batch);

        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(enrollment.getId());
        form.setGrossFee(new BigDecimal("20000"));
        form.setAcademicYear("2026-27");
        account = feeAccountService.create(form, admin);

        installmentService.createPlan(account.getId(), List.of(
                new InstallmentInput("10000", "2026-11-01", "1"),
                new InstallmentInput("10000", "2026-12-01", "2")), admin);
    }

    private static RequestPostProcessor as(UserAccount account) {
        return user(AppUserPrincipal.from(account));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return SecurityMockMvcRequestPostProcessors.csrf();
    }

    // ----- administrator ----------------------------------------------------

    @Test
    void theAdministratorCanOpenTheEditFormOfAnEligiblePlan() throws Exception {
        mockMvc.perform(get("/fees/{id}/installments/edit", account.getId()).with(as(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void theEditFormContainsTheCurrentSchedule() throws Exception {
        String html = mockMvc.perform(get("/fees/{id}/installments/edit", account.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The two current installments with their number, amount and due date.
        assertThat(html).contains("name=\"installmentNumber\"");
        assertThat(html).contains("name=\"amount\"");
        assertThat(html).contains("name=\"dueDate\"");
        assertThat(html).contains("10000.00");
        assertThat(html).contains("2026-11-01");
        assertThat(html).contains("2026-12-01");
        // The agreement the schedule belongs to.
        assertThat(html).contains("Plan Edit Web Student");
        assertThat(html).contains("20000");
        // The comparison the administrator needs.
        assertThat(html).contains("Currently scheduled");
        assertThat(html).contains("Difference from the net fee");
    }

    @Test
    void theAdministratorReplacesTheScheduleThroughTheForm() throws Exception {
        mockMvc.perform(post("/fees/{id}/installments/edit", account.getId())
                        .with(as(admin)).with(csrf())
                        .param("amount", "5000")
                        .param("dueDate", "2026-11-15")
                        .param("installmentNumber", "1")
                        .param("amount", "15000")
                        .param("dueDate", "2026-12-15")
                        .param("installmentNumber", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/fees/" + account.getId()))
                .andExpect(flash().attributeExists("successMessage"));

        List<InstallmentService.InstallmentView> views = installmentService.viewFor(account.getId());
        assertThat(views).hasSize(2);
        assertThat(views.get(0).installment().getAmount()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(views.get(0).installment().getDueDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(views.get(1).installment().getAmount()).isEqualByComparingTo(new BigDecimal("15000"));
    }

    @Test
    void anInvalidScheduleIsRejectedAndTheFormComesBackWithAMessage() throws Exception {
        String html = mockMvc.perform(post("/fees/{id}/installments/edit", account.getId())
                        .with(as(admin)).with(csrf())
                        .param("amount", "5000")
                        .param("dueDate", "2026-11-15")
                        .param("installmentNumber", "1")
                        .param("amount", "5000")
                        .param("dueDate", "2026-12-15")
                        .param("installmentNumber", "2"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("must add up to exactly the net fee");
        // The original schedule is untouched.
        assertThat(installmentService.viewFor(account.getId()).get(0).installment().getAmount())
                .isEqualByComparingTo(new BigDecimal("10000"));
    }

    @Test
    void theEditNeedsTheCsrfToken() throws Exception {
        mockMvc.perform(post("/fees/{id}/installments/edit", account.getId())
                        .with(as(admin))
                        .param("amount", "10000")
                        .param("dueDate", "2026-11-01")
                        .param("installmentNumber", "1"))
                .andExpect(status().isForbidden());
    }

    // ----- the immutable schedule -------------------------------------------

    @Test
    void anAdministratorIsRefusedWhenPaymentAllocationsMakeThePlanImmutable() throws Exception {
        PaymentForm payment = new PaymentForm();
        payment.setFeeAccountId(account.getId());
        payment.setAmount(new BigDecimal("5000"));
        payment.setPaymentDate(LocalDate.of(2026, 10, 20));
        payment.setPaymentMethod(com.omsai.coaching.fees.Payment.Method.CASH);
        paymentService.record(payment, admin);

        mockMvc.perform(get("/fees/{id}/installments/edit", account.getId()).with(as(admin)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/fees/" + account.getId()))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void aForgedEditOfAnImmutablePlanIsRefusedAndChangesNothing() throws Exception {
        PaymentForm payment = new PaymentForm();
        payment.setFeeAccountId(account.getId());
        payment.setAmount(new BigDecimal("5000"));
        payment.setPaymentDate(LocalDate.of(2026, 10, 20));
        payment.setPaymentMethod(com.omsai.coaching.fees.Payment.Method.CASH);
        paymentService.record(payment, admin);

        mockMvc.perform(post("/fees/{id}/installments/edit", account.getId())
                        .with(as(admin)).with(csrf())
                        .param("amount", "20000")
                        .param("dueDate", "2026-11-01")
                        .param("installmentNumber", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/fees/" + account.getId()))
                .andExpect(flash().attributeExists("errorMessage"));

        // The schedule is unchanged.
        assertThat(installmentService.viewFor(account.getId())).hasSize(2);
        assertThat(installmentService.viewFor(account.getId()).get(0).installment().getAmount())
                .isEqualByComparingTo(new BigDecimal("10000"));
    }

    @Test
    void theFeeAccountPageExplainsWhyTheScheduleCannotBeEdited() throws Exception {
        PaymentForm payment = new PaymentForm();
        payment.setFeeAccountId(account.getId());
        payment.setAmount(new BigDecimal("5000"));
        payment.setPaymentDate(LocalDate.of(2026, 10, 20));
        payment.setPaymentMethod(com.omsai.coaching.fees.Payment.Method.CASH);
        paymentService.record(payment, admin);

        String html = mockMvc.perform(get("/fees/{id}", account.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("payment-allocation history exists");
        // No edit action is offered.
        assertThat(html).doesNotContain("/installments/edit");
    }

    @Test
    void theFeeAccountPageOffersTheEditActionWhileThePlanIsEditable() throws Exception {
        String html = mockMvc.perform(get("/fees/{id}", account.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("/installments/edit");
        assertThat(html).contains("Edit installment plan");
    }

    // ----- teacher and student -----------------------------------------------

    @Test
    void teachersCannotOpenOrSubmitTheEditForm() throws Exception {
        UserAccount teacher = fixtures.teacherUser("planedit.web.teacher");

        mockMvc.perform(get("/fees/{id}/installments/edit", account.getId()).with(as(teacher)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/fees/{id}/installments/edit", account.getId())
                        .with(as(teacher)).with(csrf())
                        .param("amount", "20000")
                        .param("dueDate", "2026-11-01")
                        .param("installmentNumber", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentsCannotOpenOrSubmitTheEditForm() throws Exception {
        Student student = fixtures.student("Plan Edit Web Portal Student");
        UserForm form = new UserForm();
        form.setFullName(student.getFullName());
        form.setEmail("planedit.portal." + Math.abs(System.nanoTime() % 1_000_000_000L) + "@omsai.local");
        form.setRole("STUDENT");
        form.setPassword(fixtures.safePassword());
        form.setStudentId(student.getId());
        UserAccount portal = userService.create(form);

        mockMvc.perform(get("/fees/{id}/installments/edit", account.getId()).with(as(portal)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/fees/{id}/installments/edit", account.getId())
                        .with(as(portal)).with(csrf())
                        .param("amount", "20000")
                        .param("dueDate", "2026-11-01")
                        .param("installmentNumber", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousVisitorsAreRedirectedToTheLoginPage() throws Exception {
        mockMvc.perform(get("/fees/{id}/installments/edit", account.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/fees/{id}/installments/edit", account.getId()).with(csrf())
                        .param("amount", "20000")
                        .param("dueDate", "2026-11-01")
                        .param("installmentNumber", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void anUnknownFeeAccountIsAnsweredWithAMessageAndNotAnErrorPage() throws Exception {
        mockMvc.perform(get("/fees/{id}/installments/edit", account.getId() + 9999).with(as(admin)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/fees"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
