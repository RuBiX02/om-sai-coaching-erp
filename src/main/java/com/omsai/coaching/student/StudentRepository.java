package com.omsai.coaching.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

    boolean existsByStudentCodeIgnoreCase(String studentCode);

    /** The student profile with this name (used to keep a seed idempotent). */
    java.util.Optional<Student> findByFullNameIgnoreCase(String fullName);

    /** Next value of the dedicated student-code sequence. */
    @Query(value = "select nextval('student_code_seq')", nativeQuery = true)
    long nextStudentCodeValue();

    long countByActiveTrue();

    /** The student profile behind a sign-in account. */
    @Query("select s from Student s where s.userAccount.id = :userAccountId")
    java.util.Optional<Student> findByUserAccountId(@Param("userAccountId") Long userAccountId);

    boolean existsByUserAccountId(Long userAccountId);

    /** The students without a portal account (for the account creation). */
    @Query("select s from Student s where s.userAccount is null order by s.fullName")
    java.util.List<Student> findWithoutAccount();

    @Query("""
            select s from Student s
            where (:q = ''
               or lower(s.fullName) like concat('%', :q, '%')
               or lower(s.studentCode) like concat('%', :q, '%')
               or s.phone like concat('%', :q, '%'))
              and (:active is null or s.active = :active)
            """)
    Page<Student> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}
