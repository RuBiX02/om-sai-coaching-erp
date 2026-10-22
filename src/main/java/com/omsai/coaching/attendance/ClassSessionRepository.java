package com.omsai.coaching.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    /** Full context (batch subject, batch, course, subject, teacher, creator) for detail pages. */
    @Query("""
            select cs from ClassSession cs
            join fetch cs.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject csub
            join fetch csub.subject s
            left join fetch bs.teacher t
            join fetch cs.createdBy u
            where cs.id = :id
            """)
    Optional<ClassSession> findWithDetails(@Param("id") Long id);

    @Query("""
            select cs from ClassSession cs
            join fetch cs.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject csub
            join fetch csub.subject s
            left join fetch bs.teacher t
            where (:batchId is null or b.id = :batchId)
              and (:subjectId is null or s.id = :subjectId)
              and (:from is null or cs.sessionDate >= :from)
              and (:to is null or cs.sessionDate <= :to)
              and (:status is null or cs.status = :status)
            order by cs.sessionDate desc, cs.id desc
            """)
    Page<ClassSession> search(@Param("batchId") Long batchId,
                              @Param("subjectId") Long subjectId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              @Param("status") ClassSession.Status status,
                              Pageable pageable);

    /** Sessions for one batch subject (teacher's "my sessions" view may use batch filters above as well). */
    @Query("""
            select cs from ClassSession cs
            join fetch cs.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject csub
            join fetch csub.subject s
            left join fetch bs.teacher t
            where bs.batch.id = :batchId
              and (:from is null or cs.sessionDate >= :from)
              and (:to is null or cs.sessionDate <= :to)
              and (:status is null or cs.status = :status)
            order by cs.sessionDate desc, cs.id desc
            """)
    Page<ClassSession> searchForBatch(@Param("batchId") Long batchId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("status") ClassSession.Status status,
                                      Pageable pageable);

    boolean existsByBatchSubjectId(Long batchSubjectId);

    /** The session of one assignment and date, if it exists (idempotent generation). */
    @Query("""
            select cs from ClassSession cs
            where cs.batchSubject.id = :batchSubjectId
              and cs.sessionDate = :sessionDate
            """)
    Optional<ClassSession> findByBatchSubjectIdAndSessionDate(@Param("batchSubjectId") Long batchSubjectId,
                                                              @Param("sessionDate") LocalDate sessionDate);

    long countBySessionDate(LocalDate date);

    long countBySessionDateAndStatus(LocalDate date, ClassSession.Status status);

    long countByBatchSubjectId(Long batchSubjectId);

    long countByBatchSubjectIdAndStatus(Long batchSubjectId, ClassSession.Status status);

    /** Sessions of one teacher (own assignments only) - the teacher "my sessions" page. */
    @Query("""
            select cs from ClassSession cs
            join fetch cs.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject csub
            join fetch csub.subject s
            left join fetch bs.teacher t
            where bs.teacher.id = :teacherId
              and (:from is null or cs.sessionDate >= :from)
              and (:to is null or cs.sessionDate <= :to)
              and (:status is null or cs.status = :status)
            order by cs.sessionDate desc, cs.id desc
            """)
    Page<ClassSession> searchForTeacher(@Param("teacherId") Long teacherId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        @Param("status") ClassSession.Status status,
                                        Pageable pageable);

    /**
     * The classes of a teacher inside a date range. The dates are
     * always concrete values: a query with a mixture of null and
     * non-null parameters cannot be typed by the database.
     */
    @Query("""
            select cs from ClassSession cs
            join fetch cs.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject csub
            join fetch csub.subject s
            left join fetch bs.teacher t
            where bs.teacher.id = :teacherId
              and cs.sessionDate >= :from
              and cs.sessionDate <= :to
            order by cs.sessionDate desc, cs.id desc
            """)
    Page<ClassSession> searchForTeacherBetween(@Param("teacherId") Long teacherId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to,
                                               Pageable pageable);

    /** The classes of a teacher inside a date range and with one status. */
    @Query("""
            select cs from ClassSession cs
            join fetch cs.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject csub
            join fetch csub.subject s
            left join fetch bs.teacher t
            where bs.teacher.id = :teacherId
              and cs.sessionDate >= :from
              and cs.sessionDate <= :to
              and cs.status = :status
            order by cs.sessionDate desc, cs.id desc
            """)
    Page<ClassSession> searchForTeacherBetweenWithStatus(@Param("teacherId") Long teacherId,
                                                         @Param("from") LocalDate from,
                                                         @Param("to") LocalDate to,
                                                         @Param("status") ClassSession.Status status,
                                                         Pageable pageable);

    /** Sessions on one date (for the date report and the dashboard). */
    @Query("""
            select cs from ClassSession cs
            join fetch cs.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject csub
            join fetch csub.subject s
            left join fetch bs.teacher t
            where cs.sessionDate = :date
            order by cs.startTime asc nulls last, s.name
            """)
    List<ClassSession> findForDate(@Param("date") LocalDate date);
}
