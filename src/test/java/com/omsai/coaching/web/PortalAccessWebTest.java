package com.omsai.coaching.web;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.fees.FeeAccount;
import com.omsai.coaching.fees.FeeAccountForm;
import com.omsai.coaching.fees.FeeAccountService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The role isolation of the three portals: an administrator manages,
 * a teacher sees their own classes, a student sees only their own
 * records - and nobody reaches anything else by guessing an URL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PortalAccessWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private UserService userService;

    @Autowired
    private FeeAccountService feeAccountService;

    @Autowired
    private com.omsai.coaching.student.QuizRepository quizRepository;

    @Autowired
    private com.omsai.coaching.student.QuizQuestionRepository questionRepository;

    @Autowired
    private com.omsai.coaching.student.QuizAttemptRepository attemptRepository;

    @Autowired
    private com.omsai.coaching.fees.PaymentRepository paymentRepository;

    private UserAccount admin;
    private Student student;
    private Student otherStudent;
    private UserAccount studentAccount;
    private Batch batch;
    private FeeAccount feeAccount;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("portal.admin");
        Course course = fixtures.course("Portal Course");
        batch = fixtures.batch(course, "Morning");
        student = fixtures.student("Portal Student");
        otherStudent = fixtures.student("Other Portal Student");
        Enrollment enrollment = fixtures.activeEnrollment(student, batch);

        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(enrollment.getId());
        form.setGrossFee(new BigDecimal("12000"));
        form.setAcademicYear("2026-27");
        feeAccount = feeAccountService.create(form, admin);

        studentAccount = createStudentAccount(student, "portal.student");
    }

    /** A STUDENT account linked to a student profile. */
    private UserAccount createStudentAccount(Student profile, String prefix) {
        UserForm form = new UserForm();
        form.setFullName(profile.getFullName());
        form.setEmail(prefix + "." + System.nanoTime() + "@omsai.local");
        form.setRole("STUDENT");
        form.setPassword(fixtures.safePassword());
        form.setStudentId(profile.getId());
        return userService.create(form);
    }

    private static RequestPostProcessor as(UserAccount account) {
        return user(AppUserPrincipal.from(account));
    }

    /** One MCQ question of a quiz. */
    private void createQuestion(com.omsai.coaching.student.Quiz quiz, int position,
                                String text, String a, String b, String c, String d,
                                String correct) {
        com.omsai.coaching.student.QuizQuestion question = new com.omsai.coaching.student.QuizQuestion();
        question.setQuiz(quiz);
        question.setPosition(position);
        question.setQuestionText(text);
        question.setOptionA(a);
        question.setOptionB(b);
        question.setOptionC(c);
        question.setOptionD(d);
        question.setCorrectOption(correct);
        questionRepository.save(question);
    }

    // ----- the student account ---------------------------------------------

    @Test
    void aStudentAccountIsCreatedAndLinkedToItsProfile() {
        assertThat(studentAccount.getRole()).isEqualTo(UserAccount.Role.STUDENT);
        assertThat(student.getUserAccount()).isNotNull();
        assertThat(student.getUserAccount().getId()).isEqualTo(studentAccount.getId());
    }

    @Test
    void aStudentProfileCannotBeLinkedToTwoAccounts() {
        assertThatThrownBy(() -> createStudentAccount(student, "second.account"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has a portal account");
    }

    @Test
    void aStudentAccountNeedsAStudentProfile() {
        UserForm form = new UserForm();
        form.setFullName("No Student");
        form.setEmail("no.student." + System.nanoTime() + "@omsai.local");
        form.setRole("STUDENT");
        form.setPassword(fixtures.safePassword());

        assertThatThrownBy(() -> userService.create(form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Choose the student");
    }

    // ----- the student portal ----------------------------------------------

    @Test
    void theStudentPortalShowsTheOwnRecords() throws Exception {
        for (String path : new String[]{"/student", "/student/timetable", "/student/attendance",
                "/student/fees", "/student/results", "/student/profile",
                "/student/resources", "/student/quizzes"}) {
            mockMvc.perform(get(path).with(as(studentAccount))).andExpect(status().isOk());
        }

        String fees = mockMvc.perform(get("/student/fees").with(as(studentAccount)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(fees).contains(student.getFullName());
        assertThat(fees).contains(student.getStudentCode());
        assertThat(fees).contains("12000");
        // The other student is nowhere in the portal.
        assertThat(fees).doesNotContain(otherStudent.getFullName());
    }

    @Test
    void aStudentCannotReachTheManagementScreens() throws Exception {
        for (String path : new String[]{"/fees", "/payments", "/users", "/attendance/reports",
                "/examinations", "/timetable", "/dashboard", "/teachers"}) {
            mockMvc.perform(get(path).with(as(studentAccount))).andExpect(status().isForbidden());
        }
    }

    @Test
    void aStudentCannotChangeAttendanceFeesOrMarks() throws Exception {
        mockMvc.perform(post("/payments").with(as(studentAccount))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("feeAccountId", "1").param("amount", "100"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/sessions/1/attendance").with(as(studentAccount))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("status_1", "PRESENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aStudentCannotReadAnotherStudentsReport() throws Exception {
        mockMvc.perform(get("/examinations/reports/student/{id}", otherStudent.getId())
                        .with(as(studentAccount)))
                .andExpect(status().isForbidden());
    }

    // ----- the other roles --------------------------------------------------

    @Test
    void aTeacherCannotReachTheStudentPortal() throws Exception {
        UserAccount teacher = fixtures.teacherUser("portal.teacher");

        mockMvc.perform(get("/student").with(as(teacher))).andExpect(status().isForbidden());
        mockMvc.perform(get("/student/fees").with(as(teacher))).andExpect(status().isForbidden());
        mockMvc.perform(get("/student/resources").with(as(teacher)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/student/quizzes").with(as(teacher)))
                .andExpect(status().isForbidden());
    }

    // ----- the learning pack of the portal ---------------------------------

    @Test
    void aStudentSeesOnlyTheQuizzesOfTheirOwnBatchesAndIsScoredServerSide() throws Exception {
        // A quiz of ANOTHER batch: the student must not see it, reach it or score it.
        Course otherCourse = fixtures.course("Quiz Other Course");
        Batch otherBatch = fixtures.batch(otherCourse, "Evening");
        com.omsai.coaching.student.Quiz foreignQuiz = new com.omsai.coaching.student.Quiz();
        foreignQuiz.setBatch(otherBatch);
        foreignQuiz.setTitle("Foreign quiz");
        foreignQuiz.setSubjectName("Physics");
        foreignQuiz.setDurationMinutes(10);
        foreignQuiz = quizRepository.save(foreignQuiz);

        com.omsai.coaching.student.Quiz ownQuiz = new com.omsai.coaching.student.Quiz();
        ownQuiz.setBatch(batch);
        ownQuiz.setTitle("Own quiz");
        ownQuiz.setSubjectName("Mathematics");
        ownQuiz.setDurationMinutes(10);
        ownQuiz = quizRepository.save(ownQuiz);
        createQuestion(ownQuiz, 1, "1 + 1 = ?", "1", "2", "3", "4", "B");
        createQuestion(ownQuiz, 2, "2 + 2 = ?", "3", "2", "4", "5", "C");

        String list = mockMvc.perform(get("/student/quizzes").with(as(studentAccount)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("Own quiz");

        mockMvc.perform(get("/student/quizzes/" + foreignQuiz.getId()).with(as(studentAccount)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/quizzes"));

        String form = mockMvc.perform(get("/student/quizzes/" + ownQuiz.getId())
                        .with(as(studentAccount)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(form).contains("1 + 1 = ?");
        assertThat(form).doesNotContain("correctOption").doesNotContain("answer = B");

        // Two correct answers out of two → 100%. The browser sends only
        // the chosen options; the score is computed in the service.
        mockMvc.perform(post("/student/quizzes/" + ownQuiz.getId()).with(as(studentAccount))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("answers", "B").param("answers", "C"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/quizzes"));
        assertThat(attemptRepository.count()).isEqualTo(1);
        com.omsai.coaching.student.QuizAttempt attempt = attemptRepository.findAll().get(0);
        assertThat(attempt.getCorrectCount()).isEqualTo(2);
        assertThat(attempt.getTotalCount()).isEqualTo(2);

        // A retake replaces the previous attempt (still exactly one row).
        mockMvc.perform(post("/student/quizzes/" + ownQuiz.getId()).with(as(studentAccount))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("answers", "A").param("answers", "A"))
                .andExpect(status().is3xxRedirection());
        assertThat(attemptRepository.count()).isEqualTo(1);
        assertThat(attemptRepository.findAll().get(0).getCorrectCount()).isZero();
    }

    @Test
    void aStudentCannotReachAnotherStudentsFeeAgreement() throws Exception {
        mockMvc.perform(get("/student/pay").with(as(studentAccount))
                        .param("feeAccountId", "999999"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/student/pay").with(as(studentAccount))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("feeAccountId", "999999")
                        .param("amount", "1000")
                        .param("paymentMethod", "UPI"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aStudentCanRecordAPaymentAgainstTheirOwnAgreement() throws Exception {
        String form = mockMvc.perform(get("/student/pay").with(as(studentAccount))
                        .param("feeAccountId", String.valueOf(feeAccount.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(form).contains("Portal Course");

        mockMvc.perform(post("/student/pay").with(as(studentAccount))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("feeAccountId", String.valueOf(feeAccount.getId()))
                        .param("amount", "3000")
                        .param("paymentMethod", "UPI")
                        .param("referenceNumber", "UPI123DEMO")
                        .param("remarks", "Paid via the portal test."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/fees"));

        assertThat(paymentRepository.count()).isEqualTo(1);
        com.omsai.coaching.fees.Payment payment = paymentRepository.findAll().get(0);
        assertThat(payment.getAmount().compareTo(new BigDecimal("3000"))).isZero();
        assertThat(payment.getReferenceNumber()).isEqualTo("UPI123DEMO");
        assertThat(payment.getReceivedBy().getId()).isEqualTo(studentAccount.getId());
        // The payment is allocated to the installment plan immediately.
        assertThat(feeAccountService.outstandingOf(feeAccount))
                .isEqualByComparingTo("9000");
    }



    @Test
    void anAdministratorCannotReachTheStudentPortal() throws Exception {
        mockMvc.perform(get("/student").with(as(admin))).andExpect(status().isForbidden());
    }

    @Test
    void aTeacherKeepsTheFinancialScreensClosed() throws Exception {
        UserAccount teacher = fixtures.teacherUser("portal.fees.teacher");

        for (String path : new String[]{"/fees", "/fees/outstanding", "/fees/collections",
                "/payments", "/installments", "/installments/upcoming", "/installments/overdue"}) {
            mockMvc.perform(get(path).with(as(teacher))).andExpect(status().isForbidden());
        }
    }

    @Test
    void anAdministratorReachesTheManagementAndTeacherScreens() throws Exception {
        mockMvc.perform(get("/timetable").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/examinations").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/installments").with(as(admin))).andExpect(status().isOk());
    }

    @Test
    void aTeacherReachesTheTimetableAndTheExaminations() throws Exception {
        UserAccount teacher = fixtures.teacherUser("portal.teacher.pages");

        mockMvc.perform(get("/timetable").with(as(teacher))).andExpect(status().isOk());
        mockMvc.perform(get("/examinations").with(as(teacher))).andExpect(status().isOk());
        mockMvc.perform(get("/teacher").with(as(teacher))).andExpect(status().isOk());
    }

    @Test
    void anonymousVisitorsAreSentToTheLoginPage() throws Exception {
        mockMvc.perform(get("/student")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/timetable")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
