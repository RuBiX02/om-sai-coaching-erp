package com.omsai.coaching.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /** The quizzes open to one batch, newest first. */
    @Query("select q from Quiz q where q.batch.id = :batchId order by q.createdAt desc, q.id desc")
    List<Quiz> forBatch(@Param("batchId") Long batchId);

    /** Everything, newest first (the administrator's list). */
    @Query("""
            select q from Quiz q
            join fetch q.batch b
            left join fetch q.batchSubject bs
            left join fetch bs.courseSubject cs
            order by q.createdAt desc, q.id desc
            """)
    List<Quiz> findAllWithContext();

    /** The quizzes authored from the given batch-subject assignments, newest first. */
    @Query("""
            select q from Quiz q
            join fetch q.batch b
            left join fetch q.batchSubject bs
            left join fetch bs.courseSubject cs
            where bs.id in :assignmentIds
            order by q.createdAt desc, q.id desc
            """)
    List<Quiz> forAssignments(@Param("assignmentIds") Collection<Long> assignmentIds);

    /**
     * One quiz with everything its staff page renders - the batch, its
     * course and the assignment - fetched while the session is still open
     * (open-in-view is off).
     */
    @Query("""
            select q from Quiz q
            join fetch q.batch b
            join fetch b.course c
            left join fetch q.batchSubject bs
            left join fetch bs.courseSubject cs
            where q.id = :id
            """)
    Optional<Quiz> findWithContextById(@Param("id") Long id);
}
