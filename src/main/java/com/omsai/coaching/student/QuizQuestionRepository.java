package com.omsai.coaching.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findByQuizIdOrderByPositionAsc(Long quizId);

    /** The questions with their correct options (server-side scoring only). */
    @Query("""
            select q from QuizQuestion q
            where q.quiz.id = :quizId
            order by q.position
            """)
    List<QuizQuestion> forQuiz(@Param("quizId") Long quizId);

    Optional<QuizQuestion> findById(Long id);
}
