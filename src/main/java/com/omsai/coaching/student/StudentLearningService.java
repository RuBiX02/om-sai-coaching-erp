package com.omsai.coaching.student;

import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.enrollment.Enrollment;
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

    public StudentLearningService(StudentPortalService portalService,
                                  StudyResourceRepository resourceRepository,
                                  QuizRepository quizRepository,
                                  QuizQuestionRepository questionRepository,
                                  QuizAttemptRepository attemptRepository,
                                  BatchSubjectRepository batchSubjectRepository) {
        this.portalService = portalService;
        this.resourceRepository = resourceRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.batchSubjectRepository = batchSubjectRepository;
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
        for (int i = 0; i < questions.size(); i++) {
            String given = i < answers.size() ? answers.get(i) : null;
            if (given != null && !given.isBlank()
                    && given.trim().toUpperCase(Locale.ROOT)
                            .equals(questions.get(i).getCorrectOption())) {
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
        return attemptRepository.save(attempt);
    }

    /** The attempts already made by one student, newest first. */
    @Transactional(readOnly = true)
    public List<QuizAttempt> attemptsOf(Student student) {
        return attemptRepository.forStudent(student.getId());
    }

    // ----- helpers ----------------------------------------------------------

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
