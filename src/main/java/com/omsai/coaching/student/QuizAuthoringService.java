package com.omsai.coaching.student;

import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.security.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * The staff side of the quizzes. A quiz is authored from one batch-subject
 * assignment: the assignment fixes the batch and the subject, and it
 * defines the TEACHER's scope - a teacher manages only the quizzes of
 * their own active assignments, an administrator manages everything.
 */
@Service
public class QuizAuthoringService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final BatchSubjectRepository batchSubjectRepository;

    public QuizAuthoringService(QuizRepository quizRepository,
                                QuizQuestionRepository questionRepository,
                                BatchSubjectRepository batchSubjectRepository) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.batchSubjectRepository = batchSubjectRepository;
    }

    // ----- listing ----------------------------------------------------------

    /** Everything, for the administrator. */
    @Transactional(readOnly = true)
    public List<Quiz> forAdmin() {
        return quizRepository.findAllWithContext();
    }

    /** The quizzes of one teacher's assignments. */
    @Transactional(readOnly = true)
    public List<Quiz> forTeacher(UserAccount teacher) {
        List<Long> assignmentIds = batchSubjectRepository.findForUser(teacher.getId()).stream()
                .map(BatchSubject::getId)
                .toList();
        return assignmentIds.isEmpty() ? List.of() : quizRepository.forAssignments(assignmentIds);
    }

    // ----- creating a quiz --------------------------------------------------

    /**
     * Create an empty quiz from one assignment. The subject label is
     * derived from the assignment, not typed, so the catalogue can never
     * disagree with the timetable.
     */
    @Transactional
    public Quiz create(Long assignmentId, String title, String description,
                       int durationMinutes, UserAccount author) {
        BatchSubject assignment = assignmentFor(assignmentId, author);

        String cleanTitle = requireBetween(title, 3, 160, "The quiz title");
        String cleanDescription = description == null || description.strip().isEmpty()
                ? null
                : requireBetween(description.strip(), 3, 500, "The description");
        if (durationMinutes < 1 || durationMinutes > 120) {
            throw new BusinessRuleException(
                    "The duration must be between 1 and 120 minutes.");
        }
        if (!assignment.isActive()) {
            throw new BusinessRuleException(
                    "That subject assignment is not active any more - pick an active one.");
        }

        Quiz quiz = new Quiz();
        quiz.setBatch(assignment.getBatch());
        quiz.setBatchSubject(assignment);
        quiz.setSubjectName(assignment.getCourseSubject().getSubject().getName());
        quiz.setTitle(cleanTitle);
        quiz.setDescription(cleanDescription);
        quiz.setDurationMinutes(durationMinutes);
        return quizRepository.save(quiz);
    }

    // ----- questions --------------------------------------------------------

    /**
     * Add one question at the end of the quiz. The four option texts
     * and the correct letter are all required; nothing else is stored.
     */
    @Transactional
    public QuizQuestion addQuestion(Long quizId, String questionText,
                                    String optionA, String optionB,
                                    String optionC, String optionD,
                                    String correctOption, UserAccount author) {
        Quiz quiz = managed(quizId, author);
        String text = requireBetween(questionText, 3, 600, "The question");
        String a = requireBetween(optionA, 1, 300, "Option A");
        String b = requireBetween(optionB, 1, 300, "Option B");
        String c = requireBetween(optionC, 1, 300, "Option C");
        String d = requireBetween(optionD, 1, 300, "Option D");
        String correct = normalizeCorrect(correctOption);

        int nextPosition = questionRepository.findFirstByQuizIdOrderByPositionDesc(quizId)
                .map(QuizQuestion::getPosition)
                .orElse(0) + 1;
        if (nextPosition > 50) {
            throw new BusinessRuleException(
                    "A quiz holds at most 50 questions.");
        }

        QuizQuestion question = new QuizQuestion();
        question.setQuiz(quiz);
        question.setPosition(nextPosition);
        question.setQuestionText(text);
        question.setOptionA(a);
        question.setOptionB(b);
        question.setOptionC(c);
        question.setOptionD(d);
        question.setCorrectOption(correct);
        return questionRepository.save(question);
    }

    /**
     * Remove one question and close the gap: the positions stay a
     * 1..n run so the student pages never show "question 4" without a
     * question 3.
     */
    @Transactional
    public void removeQuestion(Long quizId, Long questionId, UserAccount author) {
        Quiz quiz = managed(quizId, author);
        QuizQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessRuleException("That question does not exist."));
        if (!question.getQuiz().getId().equals(quiz.getId())) {
            throw new BusinessRuleException("That question is not part of this quiz.");
        }
        questionRepository.delete(question);
        questionRepository.flush();

        int position = 0;
        for (QuizQuestion remaining : questionRepository.findByQuizIdOrderByPositionAsc(quizId)) {
            remaining.setPosition(++position);
            questionRepository.save(remaining);
        }
    }

    /** The questions of a quiz in order (staff pages may show the key). */
    @Transactional(readOnly = true)
    public List<QuizQuestion> questionsOf(Quiz quiz) {
        return questionRepository.findByQuizIdOrderByPositionAsc(quiz.getId());
    }

    // ----- scope ------------------------------------------------------------

    /** One quiz the signed-in staff member may manage. */
    @Transactional(readOnly = true)
    public Quiz managed(Long quizId, UserAccount author) {
        Quiz quiz = quizRepository.findWithContextById(quizId)
                .orElseThrow(() -> new BusinessRuleException(
                        "That quiz does not exist any more."));
        assertOwnQuiz(quiz, author);
        return quiz;
    }

    private void assertOwnQuiz(Quiz quiz, UserAccount author) {
        if (author.getRole() == UserAccount.Role.TEACHER) {
            BatchSubject assignment = quiz.getBatchSubject();
            if (assignment == null || assignment.getTeacher() == null
                    || assignment.getTeacher().getLinkedUser() == null
                    || !assignment.getTeacher().getLinkedUser().getId()
                            .equals(author.getId())) {
                throw new BusinessRuleException(
                        "You manage the quizzes of your own classes only.");
            }
        }
    }

    private BatchSubject assignmentFor(Long assignmentId, UserAccount author) {
        BatchSubject assignment = batchSubjectRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Pick the class and subject this quiz belongs to."));
        if (author.getRole() == UserAccount.Role.TEACHER) {
            if (assignment.getTeacher() == null
                    || assignment.getTeacher().getLinkedUser() == null
                    || !assignment.getTeacher().getLinkedUser().getId()
                            .equals(author.getId())) {
                throw new BusinessRuleException(
                        "You author quizzes for the subjects you teach only.");
            }
        }
        return assignment;
    }

    private static String normalizeCorrect(String correctOption) {
        String letter = correctOption == null ? "" : correctOption.trim()
                .toUpperCase(Locale.ROOT);
        if (letter.length() == 1 && "ABCD".contains(letter)) {
            return letter;
        }
        throw new BusinessRuleException(
                "Choose which option is the correct one (A to D).");
    }

    private static String requireBetween(String value, int min, int max, String what) {
        String clean = value == null ? "" : value.strip();
        if (clean.length() < min) {
            throw new BusinessRuleException(what + " needs at least " + min
                    + (min == 1 ? " character." : " characters."));
        }
        if (clean.length() > max) {
            throw new BusinessRuleException(what + " may be at most " + max + " characters.");
        }
        return clean;
    }
}
