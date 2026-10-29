package com.omsai.coaching.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.omsai.coaching.student.Quiz;
import com.omsai.coaching.student.QuizAuthoringService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The student quiz form with more than one question.
 *
 * <p>Every question is its own radio group. A shared {@code name} on all
 * questions would make one group in the browser, so answering the second
 * question would silently clear the first - the page would submit a
 * single letter. This test answers two questions through the rendered
 * form, checks that both choices remain selected, submits, and verifies
 * the review and the stored answer record.</p>
 */
@Tag("browser")
class StudentQuizBrowserTest extends BrowserTestBase {

    @Autowired
    private QuizAuthoringService quizAuthoringService;

    private Long quizId;

    @Test
    void theStudentAnswersEveryQuestionAndGetsTheReview() {
        var tree = seedBaseTree("QuizUi");
        var student = trackedStudent("Quiz UI Student");
        trackedEnrollment(student, tree.batch());
        var studentAccount = trackedStudentAccount(student, "quiz.ui");
        var admin = trackedAdminUser("quiz.ui.admin");

        Quiz quiz = quizAuthoringService.create(tree.batchSubject().getId(),
                "Quiz UI check", "Two questions answered through the real form.",
                10, admin);
        quizId = quiz.getId();
        quizAuthoringService.addQuestion(quizId, "First: 2 + 2 = ?",
                "3", "4", "5", "6", "B", admin);
        quizAuthoringService.addQuestion(quizId, "Second: 3 + 3 = ?",
                "5", "6", "7", "8", "B", admin);

        login(studentAccount.getEmail(), fixtures.safePassword());
        gotoPage("/student/quizzes/" + quizId);

        page.locator("input[name='answers[0]'][value='B']").check();
        page.locator("input[name='answers[1]'][value='B']").check();
        assertTrue(page.locator("input[name='answers[0]'][value='B']").isChecked(),
                "Answering the second question must not clear the first one.");
        assertTrue(page.locator("input[name='answers[1]'][value='B']").isChecked(),
                "The second question keeps its own answer.");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit quiz")).click();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD);

        assertTrue(page.url().endsWith("/student/quizzes/" + quizId + "/result"),
                "The submission must land on the review, got " + page.url());
        assertFlash("2 of 2");
        assertTrue(page.content().contains("2 / 2"), "The review shows the score out of two.");
        assertEquals("B,B", jdbc.queryForObject(
                        "select answers from quiz_attempts where quiz_id = ?",
                        String.class, quizId),
                "Both positions must be stored, in order.");
        assertNoClientOrServerErrors();
    }

    /** Remove the quiz rows before the shared fixture cleanup removes the tree. */
    @AfterEach
    void deleteQuizData() {
        if (quizId == null) {
            return;
        }
        jdbc.update("delete from quiz_attempts where quiz_id = ?", quizId);
        jdbc.update("delete from quiz_questions where quiz_id = ?", quizId);
        jdbc.update("delete from quizzes where id = ?", quizId);
    }
}
