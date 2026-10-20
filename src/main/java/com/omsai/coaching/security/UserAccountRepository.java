package com.omsai.coaching.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByRoleAndEmailIgnoreCase(UserAccount.Role role, String email);

    boolean existsByRole(UserAccount.Role role);

    boolean existsByEmailIgnoreCase(String email);

    long countByRoleAndActiveTrue(UserAccount.Role role);

    @Query("""
            select u from UserAccount u
            where :q = ''
               or lower(u.fullName) like concat('%', :q, '%')
               or lower(u.email) like concat('%', :q, '%')
            order by u.fullName
            """)
    Page<UserAccount> search(@Param("q") String q, Pageable pageable);

    /** Teacher-role accounts that are not yet linked to a teacher profile. */
    @Query("""
            select u from UserAccount u
            where u.role = :role
              and not exists (select t from com.omsai.coaching.teacher.Teacher t where t.linkedUser.id = u.id)
            order by u.fullName
            """)
    Page<UserAccount> findUnlinkedTeachers(@Param("role") UserAccount.Role role, Pageable pageable);
}
