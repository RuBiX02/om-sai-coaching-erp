package com.omsai.coaching.batch;

import com.omsai.coaching.teacher.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BatchSubjectRepository extends JpaRepository<BatchSubject, Long> {

    boolean existsByBatchIdAndCourseSubjectId(Long batchId, Long courseSubjectId);

    @Query("""
            select bs from BatchSubject bs
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            where bs.batch.id = :batchId
            order by s.name
            """)
    List<BatchSubject> findForBatch(@Param("batchId") Long batchId);

    @Query("""
            select bs from BatchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            where bs.teacher.id = :teacherId
            order by b.academicYear desc, c.name, s.name
            """)
    List<BatchSubject> findForTeacher(@Param("teacherId") Long teacherId);

    @Query("""
            select bs from BatchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            where bs.teacher.linkedUser.id = :userId
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
            order by b.academicYear desc, b.name, s.name
            """)
    List<BatchSubject> findForActiveBatches();
}
