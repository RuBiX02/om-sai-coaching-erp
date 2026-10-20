package com.omsai.coaching.batch;

import com.omsai.coaching.course.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    boolean existsByCourseIdAndAcademicYearAndNameIgnoreCase(Long courseId, String academicYear, String name);

    /** Single fetch with the course eagerly loaded, for detail and edit views. */
    @Query("select b from Batch b join fetch b.course where b.id = :id")
    Optional<Batch> findWithCourse(@Param("id") Long id);

    @Query("""
            select b from Batch b
            where b.course.id = :courseId
              and b.academicYear = :academicYear
              and lower(b.name) = lower(:name)
            """)
    Optional<Batch> findSameNameBatch(@Param("courseId") Long courseId,
                                      @Param("academicYear") String academicYear,
                                      @Param("name") String name);

    @Query("""
            select b from Batch b
            join fetch b.course
            where (:q = '' or lower(b.name) like concat('%', :q, '%'))
              and (:courseId is null or b.course.id = :courseId)
              and (:status is null or b.status = :status)
            order by b.academicYear desc, b.name
            """)
    Page<Batch> search(@Param("q") String q,
                       @Param("courseId") Long courseId,
                       @Param("status") Batch.Status status,
                       Pageable pageable);

    /** Active batches of active courses, for select inputs. */
    @Query("""
            select b from Batch b
            join fetch b.course c
            where b.status = :batchStatus
              and c.status = :courseStatus
            order by c.name, b.name
            """)
    List<Batch> findActiveBatches(@Param("batchStatus") Batch.Status batchStatus,
                                  @Param("courseStatus") Course.Status courseStatus);

    @Query("""
            select b from Batch b
            join fetch b.course
            where b.course.id = :courseId
            order by b.academicYear desc, b.name
            """)
    List<Batch> findForCourse(@Param("courseId") Long courseId);

    /** Every batch (any status) with its course loaded, for report pickers. */
    @Query("""
            select b from Batch b
            join fetch b.course c
            order by b.academicYear desc, b.name
            """)
    List<Batch> findAllForSelection();

    long countByStatus(Batch.Status status);
}
