package com.omsai.coaching.enrollment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /** Enrollment with student, batch and course context (fee-account pages). */
    @Query("""
            select e from Enrollment e
            join fetch e.student s
            join fetch e.batch b
            join fetch e.course c
            where e.id = :id
            """)
    Optional<Enrollment> findWithDetails(@Param("id") Long id);

    boolean existsByStudentIdAndBatchIdAndStatus(Long studentId, Long batchId, Enrollment.Status status);

    boolean existsByStudentIdAndCourseIdAndStatus(Long studentId, Long courseId, Enrollment.Status status);

    @Query("""
            select e from Enrollment e
            join fetch e.student s
            join fetch e.batch b
            join fetch e.course c
            where e.student.id = :studentId
            order by e.enrollmentDate desc, e.id desc
            """)
    List<Enrollment> findForStudent(@Param("studentId") Long studentId);

    @Query("""
            select e from Enrollment e
            join fetch e.student s
            join fetch e.batch b
            where e.batch.id = :batchId
            order by s.fullName
            """)
    List<Enrollment> findForBatch(@Param("batchId") Long batchId);

    @Query("""
            select e from Enrollment e
            join fetch e.student s
            join fetch e.batch b
            join fetch e.course c
            where e.batch.id = :batchId
              and (:status is null or e.status = :status)
            """)
    Page<Enrollment> findForBatch(@Param("batchId") Long batchId,
                                  @Param("status") Enrollment.Status status,
                                  Pageable pageable);

    @Query("""
            select e from Enrollment e
            join fetch e.student s
            join fetch e.batch b
            join fetch e.course c
            where (:q = ''
                  or lower(s.fullName) like concat('%', :q, '%')
                  or lower(s.studentCode) like concat('%', :q, '%'))
              and (:batchId is null or e.batch.id = :batchId)
              and (:status is null or e.status = :status)
            order by e.createdAt desc
            """)
    Page<Enrollment> search(@Param("q") String q,
                            @Param("batchId") Long batchId,
                            @Param("status") Enrollment.Status status,
                            Pageable pageable);

    /** Active enrollments of a student within one course. */
    @Query("""
            select e from Enrollment e
            join fetch e.batch b
            where e.student.id = :studentId
              and e.course.id = :courseId
              and e.status = :status
            """)
    List<Enrollment> findActiveInCourse(@Param("studentId") Long studentId,
                                        @Param("courseId") Long courseId,
                                        @Param("status") Enrollment.Status status);

    /** Five most recent enrollments, for the dashboard. */
    @Query("""
            select e from Enrollment e
            join fetch e.student s
            join fetch e.batch b
            join fetch e.course c
            order by e.createdAt desc
            """)
    List<Enrollment> findRecent(Pageable limit);

    /**
     * Attendance roster for a batch on a date: every enrollment of the
     * batch whose effective period covers that date, regardless of
     * current status. Uses end_date (null = still active), so students
     * who joined after the date or left before it are not eligible.
     */
    @Query("""
            select e from Enrollment e
            join fetch e.student s
            where e.batch.id = :batchId
              and e.enrollmentDate <= :date
              and (e.endDate is null or e.endDate >= :date)
            order by s.fullName, s.id
            """)
    List<Enrollment> findRosterForBatchOnDate(@Param("batchId") Long batchId, @Param("date") LocalDate date);

    long countByBatchId(Long batchId);

    long countByBatchIdAndStatus(Long batchId, Enrollment.Status status);
}
