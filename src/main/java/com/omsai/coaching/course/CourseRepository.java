package com.omsai.coaching.course;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByNameIgnoreCase(String name);

    @Query("""
            select c from Course c
            where (:q = '' or lower(c.name) like concat('%', :q, '%')
                          or lower(c.category) like concat('%', :q, '%'))
              and (:status is null or c.status = :status)
            order by c.name
            """)
    Page<Course> search(@Param("q") String q, @Param("status") Course.Status status, Pageable pageable);
    /** Active courses, for select inputs. */
    List<Course> findByStatusOrderByName(Course.Status status);

    long countByStatus(Course.Status status);
}
