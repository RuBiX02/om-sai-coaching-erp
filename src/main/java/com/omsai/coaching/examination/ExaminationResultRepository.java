package com.omsai.coaching.examination;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExaminationResultRepository extends JpaRepository<ExaminationResult, Long> {

    /** The results of one examination, with the student and the enrollment. */
    @Query("""
            select r from ExaminationResult r
            join fetch r.enrollment e
            join fetch e.student s
            join fetch e.batch b
            where r.examination.id = :examinationId
            order by s.fullName, e.id
            """)
    List<ExaminationResult> findForExamination(@Param("examinationId") Long examinationId);

    /** The results of one enrollment (the student's own academic history). */
    @Query("""
            select r from ExaminationResult r
            join fetch r.examination e
            join fetch e.batchSubject bs
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            where r.enrollment.id = :enrollmentId
            order by e.examinationDate desc, e.id desc
            """)
    List<ExaminationResult> findForEnrollment(@Param("enrollmentId") Long enrollmentId);

    Optional<ExaminationResult> findByExaminationIdAndEnrollmentId(Long examinationId, Long enrollmentId);

    /** The published results of an enrollment (what a student may see). */
    @Query("""
            select r from ExaminationResult r
            join fetch r.examination e
            join fetch e.batchSubject bs
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            where r.enrollment.id = :enrollmentId
              and r.published = true
              and e.status = 'PUBLISHED'
            order by e.examinationDate desc, e.id desc
            """)
    List<ExaminationResult> findPublishedForEnrollment(@Param("enrollmentId") Long enrollmentId);
}
