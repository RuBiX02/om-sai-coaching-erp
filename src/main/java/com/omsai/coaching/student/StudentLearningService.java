package com.omsai.coaching.student;

import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.enrollment.Enrollment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The learning content of the student portal: study resources and
 * quizzes.
 *
 * <p>Everything a student gets here is filtered by the batches their
 * ACTIVE enrollments point at - a student can only ever see material of
 * the classes they actually attend, and can only submit attempts for
 * those classes. The score is computed here, in the server, from the
 * correct options; nothing the browser posts is trusted.</p>
 */
@Service
public class StudentLearningService {

    private final StudentPortalService portalService;
    private final StudyResourceRepository resourceRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final BatchSubjectRepository batchSubjectRepository;
    private final NamedParameterJdbcTemplate jdbc;

    public StudentLearningService(StudentPortalService portalService,
                                  StudyResourceRepository resourceRepository,
                                  QuizRepository quizRepository,
                                  QuizQuestionRepository questionRepository,
                                  QuizAttemptRepository attemptRepository,
                                  BatchSubjectRepository batchSubjectRepository,
                                  NamedParameterJdbcTemplate jdbc) {
        this.portalService = portalService;
        this.resourceRepository = resourceRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.batchSubjectRepository = batchSubjectRepository;
        this.jdbc = jdbc;
    }

    /** The study resources of the student's active batches (theirs first). */
    @Transactional(readOnly = true)
    public List<StudyResource> resourcesOf(Student student) {
        List<Long> subjectIds = new ArrayList<>();
        for (Long batchId : activeBatchIds(student)) {
            for (BatchSubject bs : batchSubjectRepository.findForBatch(batchId)) {
                subjectIds.add(bs.getCourseSubject().getSubject().getId());
            }
        }
        if (subjectIds.isEmpty()) {
            return List.of();
        }
        List<StudyResource> all = new ArrayList<>();
        for (Long batchId : activeBatchIds(student)) {
            all.addAll(resourceRepository.visibleTo(batchId, subjectIds));
        }
        return all.stream().distinct()
                .sorted(java.util.Comparator
                        .comparing((StudyResource r) -> r.getSubject() == null ? "" : r.getSubject().getName())
                        .thenComparing(StudyResource::getTitle))
                .toList();
    }

    /** The quizzes open to the student's active batches, newest first. */
    @Transactional(readOnly = true)
    public List<Quiz> quizzesOf(Student student) {
        List<Quiz> quizzes = new ArrayList<>();
        for (Long batchId : activeBatchIds(student)) {
            quizzes.addAll(quizRepository.forBatch(batchId));
        }
        return quizzes.stream().distinct().toList();
    }

    /** One quiz by id (the caller decides what the student may do with it). */
    @Transactional(readOnly = true)
    public Quiz quizOf(Long id) {
        return quizRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Quiz not found."));
    }

    /**
     * One quiz open to the student (its questions WITHOUT the correct
     * options - the page must never carry the answers).
     */
    @Transactional(readOnly = true)
    public Quiz quizFor(Quiz quiz, Student student) {
        if (!activeBatchIds(student).contains(quiz.getBatch().getId())) {
            throw new BusinessRuleException("This quiz does not belong to your classes.");
        }
        return quiz;
    }

    /** The questions of a quiz in order. */
    public List<QuizQuestion> questionsOf(Quiz quiz) {
        return questionRepository.findByQuizIdOrderByPositionAsc(quiz.getId());
    }

    /**
     * Submit the student's answers and score them server-side. A second
     * submission replaces the first (the attempt table keeps exactly one
     * entry per student and quiz, so the history reflects the newest
     * result).
     *
     * @param answers one entry per question, by position: A, B, C, D or
     *                null (an unanswered question is simply wrong).
     */
    @Transactional
    public QuizAttempt submitAttempt(Quiz quiz, Student student,
                                     com.omsai.coaching.security.UserAccount account,
                                     List<String> answers) {
        quizFor(quiz, student); // membership check
        List<QuizQuestion> questions = questionRepository.forQuiz(quiz.getId());
        if (questions.isEmpty()) {
            throw new BusinessRuleException("This quiz has no questions yet.");
        }
        int correct = 0;
        List<String> recorded = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            String given = i < answers.size() ? answers.get(i) : null;
            String normalized = normalize(given);
            recorded.add(normalized);
            if (normalized != null && !normalized.isBlank()
                    && normalized.equals(questions.get(i).getCorrectOption())) {
                correct++;
            }
        }
        attemptRepository.findByQuizIdAndStudentId(quiz.getId(), student.getId())
                .ifPresent(previous -> {
                    attemptRepository.delete(previous);
                    // Flush now so the DELETE reaches the database before
                    // the new INSERT: the unique key (quiz, student) would
                    // otherwise see both rows during the flush.
                    attemptRepository.flush();
                });
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setStudent(student);
        attempt.setAccount(account);
        attempt.setCorrectCount(correct);
        attempt.setTotalCount(questions.size());
        // The letters are stored so the portal can review the attempt
        // later; the browser posts them again anyway on a retake.
        attempt.setAnswers(String.join(",", recorded));
        return attemptRepository.save(attempt);
    }

    /** The attempt already made for one quiz, or null when not attempted. */
    @Transactional(readOnly = true)
    public QuizAttempt attemptFor(Quiz quiz, Student student) {
        return attemptRepository.findByQuizIdAndStudentId(quiz.getId(), student.getId())
                .orElse(null);
    }

    /**
     * One review line per question: the question, the letter the
     * student answered and whether the server scored it as correct.
     * Showing the correct answer here is the point of a review - it
     * happens only AFTER a submission for the student's own attempt.
     */
    public List<ReviewRow> reviewOf(Quiz quiz, QuizAttempt attempt) {
        List<QuizQuestion> questions = questionRepository.forQuiz(quiz.getId());
        List<String> given = parseAnswers(attempt.getAnswers(), questions.size());
        List<ReviewRow> rows = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            String answer = i < given.size() ? given.get(i) : null;
            boolean scoredCorrect = answer != null && !answer.isBlank()
                    && answer.equals(questions.get(i).getCorrectOption());
            rows.add(new ReviewRow(i + 1, questions.get(i), answer, scoredCorrect));
        }
        return rows;
    }

    /** One line of the attempt review. */
    public record ReviewRow(int position, QuizQuestion question,
                            String given, boolean correct) {

        /** The text of the chosen option ("Not answered" when skipped). */
        public String givenText() {
            return optionText(question, given);
        }

        /** The text of the correct option. */
        public String correctText() {
            return optionText(question, question.getCorrectOption());
        }

        private static String optionText(QuizQuestion question, String letter) {
            if (letter == null || letter.isBlank()) {
                return "Not answered";
            }
            return switch (letter.toUpperCase(Locale.ROOT)) {
                case "A" -> question.getOptionA();
                case "B" -> question.getOptionB();
                case "C" -> question.getOptionC();
                case "D" -> question.getOptionD();
                default -> "Not answered";
            };
        }
    }

    // ----- recent class topics ----------------------------------------------

    /**
     * The topics of the student's COMPLETED class sessions, newest
     * first, across their batches. This is honest "what has been
     * taught" - derived from the session register, not invented.
     */
    @Transactional(readOnly = true)
    public List<TopicRow> recentTopics(Student student) {
        List<Long> batchIds = activeBatchIds(student);
        if (batchIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT s.name AS subject, cs.topic AS topic, cs.session_date AS session_date
                FROM class_sessions cs
                JOIN batch_subjects bs ON bs.id = cs.batch_subject_id
                JOIN course_subjects csu ON csu.id = bs.course_subject_id
                JOIN subjects s ON s.id = csu.subject_id
                WHERE cs.status = 'COMPLETED' AND bs.batch_id IN (:batchIds)
                ORDER BY cs.session_date DESC, cs.id DESC
                LIMIT 24
                """,
                new MapSqlParameterSource().addValue("batchIds", batchIds),
                (rs, i) -> new TopicRow(rs.getString("subject"), rs.getString("topic"),
                        rs.getDate("session_date").toLocalDate()));
    }

    /** One covered topic of the class register. */
    public record TopicRow(String subject, String topic, java.time.LocalDate date) {
    }

    /** The attempts already made by one student, newest first. */
    @Transactional(readOnly = true)
    public List<QuizAttempt> attemptsOf(Student student) {
        return attemptRepository.forStudent(student.getId());
    }

    // ----- helpers ----------------------------------------------------------

    /** One letter per question position ('-' for skipped), space trimmed. */
    private static String normalize(String given) {
        if (given == null || given.isBlank()) {
            return "-";
        }
        String letter = given.trim().toUpperCase(Locale.ROOT);
        return switch (letter) {
            case "A", "B", "C", "D" -> letter;
            default -> "-";
        };
    }

    /** Split the stored answer letters back into one entry per question. */
    private static List<String> parseAnswers(String stored, int questionCount) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(stored.split(",", -1)).limit(questionCount).toList();
    }

    private List<Long> activeBatchIds(Student student) {
        Set<Long> ids = new HashSet<>();
        for (Enrollment enrollment : portalService.enrollmentsOf(student)) {
            if (enrollment.getStatus() == Enrollment.Status.ACTIVE) {
                ids.add(enrollment.getBatch().getId());
            }
        }
        return new ArrayList<>(ids);
    }
}
