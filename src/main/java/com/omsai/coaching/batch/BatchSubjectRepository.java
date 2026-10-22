package com.omsai.coaching.batch;

import com.omsai.coaching.teacher.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BatchSubjectRepository extends JpaRepository<BatchSubject, Long> {

    boolean existsByBatchIdAndCourseSubjectId(Long batchId, Long courseSubjectId);

    /** True when the batch already teaches the subject as an ACTIVE assignment. */
    @Query("""
            select count(bs) > 0 from BatchSubject bs
            where bs.batch.id = :batchId
              and bs.courseSubject.id = :courseSubjectId
              and bs.active = true
            """)
    boolean existsActiveByBatchIdAndCourseSubjectId(@Param("batchId") Long batchId,
                                                     @Param("courseSubjectId") Long courseSubjectId);

    /**
     * Every assignment of one batch, active ones first. Deactivated
     * assignments stay visible on the batch page so that they can be
     * reactivated; they are only hidden from the pickers.
     */
    @Query("""
            select bs from BatchSubject bs
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            where bs.batch.id = :batchId
            order by bs.active desc, s.name
            """)
    List<BatchSubject> findForBatch(@Param("batchId") Long batchId);

    /**
     * The ACTIVE assignments of one teacher. A deactivated assignment
     * is not taught any more, so it must not show up as teaching work.
     */
    @Query("""
            select bs from BatchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            where bs.teacher.id = :teacherId
              and bs.active = true
            order by b.academicYear desc, c.name, s.name
            """)
    List<BatchSubject> findForTeacher(@Param("teacherId") Long teacherId);

    /**
     * The ACTIVE assignments of the teacher behind a login account.
     */
    @Query("""
            select bs from BatchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            where bs.teacher.linkedUser.id = :userId
              and bs.active = true
            order by b.academicYear desc, s.name
            """)
    List<BatchSubject> findForUser(@Param("userId") Long userId);

    /** Assignments (across any batch) for one course-subject pair. */
    long countByCourseSubjectId(Long courseSubjectId);

    long countByTeacher(Teacher teacher);

    /** One assignment with full context, for the subject-wise report. */
    @Query("""
            select bs from BatchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            where bs.batch.id = :batchId
              and bs.courseSubject.id = :courseSubjectId
            """)
    Optional<BatchSubject> findByBatchIdAndCourseSubjectId(@Param("batchId") Long batchId,
                                                           @Param("courseSubjectId") Long courseSubjectId);

    /**
     * All assignments of active batches of active courses - the pool an
     * administrator may pick from when scheduling a new class session.
     * Deactivated assignments are left out: no new class may be
     * scheduled for a subject that is not being taught any more.
     */
    @Query("""
            select bs from BatchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            where b.status = com.omsai.coaching.batch.Batch.Status.ACTIVE
              and c.status = com.omsai.coaching.course.Course.Status.ACTIVE
              and bs.active = true
            order by b.academicYear desc, b.name, s.name
            """)
    List<BatchSubject> findForActiveBatches();

    /**
     * The ACTIVE assignments with their batch, course and subject (for
     * the pickers).
     */
    @Query("""
            select bs from BatchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            where bs.active = true
            order by c.name, b.name, s.name
            """)
    List<BatchSubject> findAllWithContext();
}
