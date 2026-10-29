package com.omsai.coaching.examination;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExaminationRepository extends JpaRepository<Examination, Long> {

    /** One examination with its assignment, batch, subject and teacher. */
    @Query("""
            select e from Examination e
            join fetch e.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            join fetch e.createdBy u
            where e.id = :id
            """)
    Optional<Examination> findWithDetails(@Param("id") Long id);

    /** Every examination with its context, newest first. */
    @Query("""
            select e from Examination e
            join fetch e.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            join fetch e.createdBy u
            order by e.examinationDate desc, e.id desc
            """)
    List<Examination> findAllWithContext();

    /** The examinations of one assignment. */
    @Query("""
            select e from Examination e
            join fetch e.batchSubject bs
            join fetch bs.batch b
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            where bs.id = :batchSubjectId
            order by e.examinationDate desc
            """)
    List<Examination> findForBatchSubject(@Param("batchSubjectId") Long batchSubjectId);

    /** How many examinations point at one assignment (a deletion guard). */
    long countByBatchSubjectId(Long batchSubjectId);

    /** The examinations of the subjects a teacher is assigned to. */
    @Query("""
            select e from Examination e
            join fetch e.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            where bs.teacher.id = :teacherId
            order by e.examinationDate desc, e.id desc
            """)
    List<Examination> findForTeacher(@Param("teacherId") Long teacherId);

    /** The examinations of a batch (optionally limited to a date range). */
    @Query("""
            select e from Examination e
            join fetch e.batchSubject bs
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            where bs.batch.id = :batchId
              and (cast(:from as date) is null or e.examinationDate >= cast(:from as date))
              and (cast(:to as date) is null or e.examinationDate <= cast(:to as date))
            order by e.examinationDate desc
            """)
    List<Examination> findForBatch(@Param("batchId") Long batchId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    /**
     * The published examinations still ahead for the given batches -
     * the exam schedule shown to the student portal. Draft and
     * cancelled examinations are never part of it.
     */
    @Query("""
            select e from Examination e
            join fetch e.batchSubject bs
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            where bs.batch.id in :batchIds
              and e.status = com.omsai.coaching.examination.Examination.Status.PUBLISHED
              and e.examinationDate >= :today
            order by e.examinationDate, e.id
            """)
    List<Examination> findUpcomingForBatches(@Param("batchIds") Collection<Long> batchIds,
                                             @Param("today") LocalDate today);
}
