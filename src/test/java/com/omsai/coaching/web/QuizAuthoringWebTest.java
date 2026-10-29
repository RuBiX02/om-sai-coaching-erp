package com.omsai.coaching.web;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.CourseSubject;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserForm;
import com.omsai.coaching.security.UserService;
import com.omsai.coaching.student.Quiz;
import com.omsai.coaching.student.QuizQuestion;
import com.omsai.coaching.student.QuizQuestionRepository;
import com.omsai.coaching.student.QuizRepository;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.subject.Subject;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The staff quiz screens: an administrator manages every quiz, a
 * teacher only the quizzes of their own assignments, and the student
 * review reveals the correct options only after a submission.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QuizAuthoringWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private UserService userService;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizQuestionRepository questionRepository;

    private UserAccount admin;
    private Batch batch;
    private Student student;
    private UserAccount studentAccount;
    private BatchSubject ownAssignment;
    private BatchSubject foreignAssignment;
    private UserAccount teacherA;
    private UserAccount teacherB;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("quiz.admin");
        Course course = fixtures.course("Quiz Author Course");
        batch = fixtures.batch(course, "Morning");
        student = fixtures.student("Quiz Author Student");
        fixtures.activeEnrollment(student, batch);
        studentAccount = createStudentAccount(student, "quiz.student");

        Subject physics = fixtures.subject("Physics");
        CourseSubject physicsCourse = fixtures.assign(course, physics);
        ownAssignment = fixtures.assignCourseSubject(batch, physicsCourse);

        Subject chemistry = fixtures.subject("Chemistry");
        CourseSubject chemistryCourse = fixtures.assign(course, chemistry);
        foreignAssignment = fixtures.assignCourseSubject(batch, chemistryCourse);

        teacherA = fixtures.teacherUser("quiz.teacher.a");
        fixtures.assignTeacher(ownAssignment,
                fixtures.teacherLinkedTo(teacherA, "Quiz Teacher A"));
        teacherB = fixtures.teacherUser("quiz.teacher.b");
        fixtures.assignTeacher(foreignAssignment,
                fixtures.teacherLinkedTo(teacherB, "Quiz Teacher B"));
    }

    @Test
    void anAdminCreatesAQuizAndAddsAQuestion() throws Exception {
        mockMvc.perform(post("/quizzes").with(as(admin))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("batchSubjectId", String.valueOf(ownAssignment.getId()))
                        .param("title", "Kinematics basics")
                        .param("description", "Five questions on motion.")
                        .param("durationMinutes", "10"))
                .andExpect(status().is3xxRedirection());

        assertThat(quizRepository.count()).isEqualTo(1);
        Quiz quiz = quizRepository.findAll().get(0);
        assertThat(quiz.getTitle()).isEqualTo("Kinematics basics");
        // The subject label comes from the assignment, not the browser.
        assertThat(quiz.getSubjectName()).startsWith("Physics");
        assertThat(quiz.getBatch().getId()).isEqualTo(batch.getId());
        assertThat(quiz.getBatchSubject().getId()).isEqualTo(ownAssignment.getId());

        mockMvc.perform(post("/quizzes/" + quiz.getId() + "/questions").with(as(admin))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("questionText", "The SI unit of acceleration is")
                        .param("optionA", "m/s")
                        .param("optionB", "m/s²")
                        .param("optionC", "N")
                        .param("optionD", "kg")
                        .param("correctOption", "B"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/quizzes/" + quiz.getId()));

        assertThat(questionRepository.count()).isEqualTo(1);
        QuizQuestion question = questionRepository.findAll().get(0);
        assertThat(question.getPosition()).isEqualTo(1);
        assertThat(question.getCorrectOption()).isEqualTo("B");

        // The management page shows the correct key to the staff.
        String manage = mockMvc.perform(get("/quizzes/" + quiz.getId()).with(as(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(manage).contains("The SI unit of acceleration is").contains("m/s²");
    }

    @Test
    void aTeacherAuthorsOnlyForTheirOwnAssignment() throws Exception {
        mockMvc.perform(post("/quizzes").with(as(teacherA))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("batchSubjectId", String.valueOf(ownAssignment.getId()))
                        .param("title", "Teacher A quiz")
                        .param("durationMinutes", "10"))
                .andExpect(status().is3xxRedirection());
        assertThat(quizRepository.count()).isEqualTo(1);

        // Another teacher's assignment is refused before anything is written.
        mockMvc.perform(post("/quizzes").with(as(teacherA))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("batchSubjectId", String.valueOf(foreignAssignment.getId()))
                        .param("title", "Borrowed quiz")
                        .param("durationMinutes", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/quizzes/new"));
        assertThat(quizRepository.count()).isEqualTo(1);

        // The list and the create form show the teacher their own subject only.
        String teacherList = mockMvc.perform(get("/quizzes").with(as(teacherA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(teacherList).contains("Teacher A quiz");

        String otherList = mockMvc.perform(get("/quizzes").with(as(teacherB)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(otherList).doesNotContain("Teacher A quiz");

        String form = mockMvc.perform(get("/quizzes/new").with(as(teacherA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(form).contains("Physics").doesNotContain("Chemistry");
    }

    @Test
    void aTeacherCannotManageAnotherTeachersQuiz() throws Exception {
        Quiz foreign = createQuiz(foreignAssignment, "Quizzes of teacher B");

        mockMvc.perform(get("/quizzes/" + foreign.getId()).with(as(teacherA)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/quizzes"));

        mockMvc.perform(post("/quizzes/" + foreign.getId() + "/questions").with(as(teacherA))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("questionText", "Not mine to add")
                        .param("optionA", "a").param("optionB", "b")
                        .param("optionC", "c").param("optionD", "d")
                        .param("correctOption", "A"))
                .andExpect(status().is3xxRedirection());
        assertThat(questionRepository.count()).isZero();
    }

    @Test
    void removingAQuestionKeepsTheNumberingGapless() throws Exception {
        Quiz quiz = createQuiz(ownAssignment, "Renumber quiz");
        createQuestion(quiz, 1, "First question?", "a1", "b1", "c1", "d1", "A");
        createQuestion(quiz, 2, "Second question?", "a2", "b2", "c2", "d2", "B");
        Long firstId = questionRepository.findAll().get(0).getId();

        mockMvc.perform(post("/quizzes/" + quiz.getId() + "/questions/" + firstId + "/remove")
                        .with(as(admin))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/quizzes/" + quiz.getId()));

        assertThat(questionRepository.count()).isEqualTo(1);
        assertThat(questionRepository.findAll().get(0).getPosition()).isEqualTo(1);
        assertThat(questionRepository.findAll().get(0).getQuestionText())
                .isEqualTo("Second question?");
    }

    @Test
    void theReviewShowsTheGivenAndCorrectAnswersAfterSubmission() throws Exception {
        Quiz quiz = createQuiz(ownAssignment, "Reviewable quiz");
        createQuestion(quiz, 1, "Capital of Maharashtra?", "Mumbai", "Pune", "Nagpur", "Nashik", "A");
        createQuestion(quiz, 2, "2 + 3 = ?", "4", "5", "6", "7", "B");

        mockMvc.perform(post("/student/quizzes/" + quiz.getId())
                        .with(as(studentAccount))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("answers[0]", "A").param("answers[1]", "C"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/quizzes/" + quiz.getId() + "/result"));

        var attempt = quizAttempts().get(0);
        assertThat(attempt.getAnswers()).isEqualTo("A,C");
        assertThat(attempt.getCorrectCount()).isEqualTo(1);

        String review = mockMvc.perform(get("/student/quizzes/" + quiz.getId() + "/result")
                        .with(as(studentAccount)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(review).contains("Capital of Maharashtra?")
                .contains("Correct")
                .contains("Incorrect")
                .contains("50%");
    }

    @Test
    void aStudentCannotUseTheStaffQuizScreens() throws Exception {
        mockMvc.perform(get("/quizzes").with(as(studentAccount)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/quizzes").with(as(studentAccount))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("batchSubjectId", String.valueOf(ownAssignment.getId()))
                        .param("title", "Not allowed")
                        .param("durationMinutes", "10"))
                .andExpect(status().isForbidden());
        assertThat(quizRepository.count()).isZero();
    }

    // ----- helpers ----------------------------------------------------------

    private Quiz createQuiz(BatchSubject assignment, String title) {
        Quiz quiz = new Quiz();
        quiz.setBatch(assignment.getBatch());
        quiz.setBatchSubject(assignment);
        quiz.setSubjectName(assignment.getCourseSubject().getSubject().getName());
        quiz.setTitle(title);
        quiz.setDurationMinutes(10);
        return quizRepository.save(quiz);
    }

    private void createQuestion(Quiz quiz, int position, String text,
                                String a, String b, String c, String d, String correct) {
        QuizQuestion question = new QuizQuestion();
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

    private java.util.List<com.omsai.coaching.student.QuizAttempt> quizAttempts() {
        return attemptRepository.findAll();
    }

    @Autowired
    private com.omsai.coaching.student.QuizAttemptRepository attemptRepository;

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
}
