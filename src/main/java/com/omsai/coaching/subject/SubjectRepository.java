package com.omsai.coaching.subject;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    Optional<Subject> findByNameIgnoreCase(String name);

    /**
     * Locks the subject row (SELECT ... FOR UPDATE) so a deletion and a
     * concurrent course assignment cannot cross. The lock of the
     * referenced row also blocks the foreign-key check of a concurrent
     * INSERT into course_subjects, so the "is it still unused?" check
     * below cannot become stale.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subject s where s.id = :id")
    Optional<Subject> getForUpdate(@Param("id") Long id);

    /**
     * The subjects of the catalogue. Inactive subjects stay in the list:
     * the administrator has to be able to see them and reactivate them.
     */
    @Query("""
            select s from Subject s
            where :q = '' or lower(s.name) like concat('%', :q, '%')
            order by s.name
            """)
    Page<Subject> search(@Param("q") String q, Pageable pageable);
}
