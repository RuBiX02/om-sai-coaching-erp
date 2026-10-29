package com.omsai.coaching.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /** The quizzes open to one batch, newest first. */
    @Query("select q from Quiz q where q.batch.id = :batchId order by q.createdAt desc, q.id desc")
    List<Quiz> forBatch(@Param("batchId") Long batchId);
}
