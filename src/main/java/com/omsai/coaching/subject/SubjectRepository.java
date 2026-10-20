package com.omsai.coaching.subject;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    Optional<Subject> findByNameIgnoreCase(String name);

    @Query("""
            select s from Subject s
            where :q = '' or lower(s.name) like concat('%', :q, '%')
            order by s.name
            """)
    Page<Subject> search(@Param("q") String q, Pageable pageable);
}
