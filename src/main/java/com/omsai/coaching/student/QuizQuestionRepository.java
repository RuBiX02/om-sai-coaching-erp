package com.omsai.coaching.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findByQuizIdOrderByPositionAsc(Long quizId);

    /** The last question in order, for computing the next position. */
    Optional<QuizQuestion> findFirstByQuizIdOrderByPositionDesc(Long quizId);

    /** The number of questions per quiz, for the staff list page. */
    @Query("select q.quiz.id, count(q) from QuizQuestion q group by q.quiz.id")
    List<Object[]> countPerQuiz();

    /** The questions with their correct options (server-side scoring only). */
    @Query("""
            select q from QuizQuestion q
            where q.quiz.id = :quizId
            order by q.position
            """)
    List<QuizQuestion> forQuiz(@Param("quizId") Long quizId);

    Optional<QuizQuestion> findById(Long id);
}
