package com.omsai.coaching.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    /** All attempts of one student, newest first. */
    @Query("""
            select a from QuizAttempt a
            where a.student.id = :studentId
            order by a.submittedAt desc, a.id desc
            """)
    List<QuizAttempt> forStudent(@Param("studentId") Long studentId);

    Optional<QuizAttempt> findByQuizIdAndStudentId(Long quizId, Long studentId);
}
