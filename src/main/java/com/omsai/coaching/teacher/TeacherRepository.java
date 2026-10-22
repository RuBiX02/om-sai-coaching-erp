package com.omsai.coaching.teacher;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    boolean existsByLinkedUserId(Long userId);

    Optional<Teacher> findByLinkedUserId(Long userId);

    /** The teacher profile with this name (used to keep a seed idempotent). */
    Optional<Teacher> findByFullNameIgnoreCase(String fullName);

    @Query("""
            select t from Teacher t
            left join fetch t.linkedUser
            where t.id = :id
            """)
    Optional<Teacher> findWithLinkedUser(@Param("id") Long id);

    @Query("""
            select t from Teacher t
            where (:q = ''
               or lower(t.fullName) like concat('%', :q, '%')
               or t.phone like concat('%', :q, '%')
               or lower(t.email) like concat('%', :q, '%'))
              and (:active is null or t.active = :active)
            """)
    Page<Teacher> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);

    @Query("select t from Teacher t where t.active = true order by t.fullName")
    List<Teacher> findActiveTeachers();

    long countByActiveTrue();
}
