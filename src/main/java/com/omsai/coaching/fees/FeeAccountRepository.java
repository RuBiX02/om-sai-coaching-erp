package com.omsai.coaching.fees;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FeeAccountRepository extends JpaRepository<FeeAccount, Long> {

    /** Full context (student, course, enrollment+batch) for pages. */
    @Query("""
            select fa from FeeAccount fa
            join fetch fa.student s
            join fetch fa.course c
            join fetch fa.enrollment e
            join fetch e.batch b
            join fetch fa.createdBy u
            where fa.id = :id
            """)
    Optional<FeeAccount> findWithDetails(@Param("id") Long id);

    boolean existsByStudentIdAndCourseIdAndAcademicYear(Long studentId, Long courseId, String academicYear);

    /** The fee agreements of one student (one per course and year). */
    @Query("""
            select fa from FeeAccount fa
            join fetch fa.course c
            join fetch fa.enrollment e
            join fetch e.batch b
            where fa.student.id = :studentId
            order by c.name, fa.academicYear
            """)
    List<FeeAccount> findByStudentId(@Param("studentId") Long studentId);

    Optional<FeeAccount> findByStudentIdAndCourseIdAndAcademicYear(Long studentId, Long courseId, String academicYear);

    /**
     * Locks the account row (SELECT ... FOR UPDATE) so concurrent
     * payment submissions serialize instead of both accepting an
     * overpayment. Used by PaymentService for record() and void().
     */
    @Query("""
            select fa from FeeAccount fa
            where fa.id = :id
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FeeAccount> getForUpdate(@Param("id") Long id);

    /** Sum of RECEIVED payments for one account (never counts voided payments). */
    @Query(value = """
            select coalesce(sum(p.amount), 0)
            from payments p
            where p.fee_account_id = :accountId
              and p.status = 'RECEIVED'
            """, nativeQuery = true)
    BigDecimal receivedTotal(@Param("accountId") Long accountId);

    /**
     * Accounts with their computed outstanding balance
     * (net fee minus valid payments). mode=0: everything;
     * mode=1: only outstanding > 0; mode=2: only overdue
     * (due date passed and still outstanding).
     */
    @Query(value = """
            select fa.id as id,
                   s.full_name as studentName,
                   s.student_code as studentCode,
                   c.name as courseName,
                   fa.academic_year as academicYear,
                   fa.gross_fee as grossFee,
                   fa.discount_amount as discountAmount,
                   fa.due_date as dueDate,
                   (fa.gross_fee - fa.discount_amount) - coalesce(pt.total, 0) as outstanding
            from fee_accounts fa
            join students s on s.id = fa.student_id
            join courses c on c.id = fa.course_id
            left join (select fee_account_id, sum(amount) as total
                       from payments
                       where status = 'RECEIVED'
                       group by fee_account_id) pt on pt.fee_account_id = fa.id
            where (:mode = 0
                   or (:mode = 1 and (fa.gross_fee - fa.discount_amount) - coalesce(pt.total, 0) > 0)
                   or (:mode = 2 and (fa.gross_fee - fa.discount_amount) - coalesce(pt.total, 0) > 0
                       and fa.due_date is not null and fa.due_date < :today))
              and (:courseId is null or fa.course_id = :courseId)
              and (:academicYear = '' or fa.academic_year = :academicYear)
              and (:q = '' or lower(s.full_name) like concat('%', :q, '%')
                            or lower(s.student_code) like concat('%', :q, '%'))
            order by s.full_name, fa.id
            """,
            countQuery = """
            select count(*)
            from fee_accounts fa
            join students s on s.id = fa.student_id
            left join (select fee_account_id, sum(amount) as total
                       from payments
                       where status = 'RECEIVED'
                       group by fee_account_id) pt on pt.fee_account_id = fa.id
            where (:mode = 0
                   or (:mode = 1 and (fa.gross_fee - fa.discount_amount) - coalesce(pt.total, 0) > 0)
                   or (:mode = 2 and (fa.gross_fee - fa.discount_amount) - coalesce(pt.total, 0) > 0
                       and fa.due_date is not null and fa.due_date < :today))
              and (:courseId is null or fa.course_id = :courseId)
              and (:academicYear = '' or fa.academic_year = :academicYear)
              and (:q = '' or lower(s.full_name) like concat('%', :q, '%')
                            or lower(s.student_code) like concat('%', :q, '%'))
            """,
            nativeQuery = true)
    Page<FeeAccountRow> search(@Param("mode") int mode,
                               @Param("today") LocalDate today,
                               @Param("courseId") Long courseId,
                               @Param("academicYear") String academicYear,
                               @Param("q") String q,
                               Pageable pageable);

    /** Total outstanding balance across all accounts (dashboard). */
    @Query(value = """
            select coalesce(sum(fa.gross_fee - fa.discount_amount), 0)
                 - coalesce((select sum(p.amount) from payments p where p.status = 'RECEIVED'), 0)
            from fee_accounts fa
            """, nativeQuery = true)
    BigDecimal totalOutstanding();

    /** Accounts overdue as of today: due date passed and balance still outstanding. */
    @Query(value = """
            select count(*) from fee_accounts fa
            where (fa.gross_fee - fa.discount_amount)
                        - coalesce((select sum(p.amount) from payments p
                                    where p.fee_account_id = fa.id and p.status = 'RECEIVED'), 0) > 0
              and fa.due_date is not null
              and fa.due_date < :today
            """, nativeQuery = true)
    long countOverdue(@Param("today") LocalDate today);

    /**
     * Total outstanding balance of the accounts that have an
     * installment plan. It must equal the sum of the remaining
     * installment balances of those accounts - the reconciliation
     * invariant of the installment module.
     */
    @Query(value = """
            select coalesce(sum((fa.gross_fee - fa.discount_amount) - coalesce(pt.total, 0)), 0)
            from fee_accounts fa
            left join (select fee_account_id, sum(amount) as total
                       from payments
                       where status = 'RECEIVED'
                       group by fee_account_id) pt on pt.fee_account_id = fa.id
            where exists (select 1 from fee_installments i where i.fee_account_id = fa.id)
            """, nativeQuery = true)
    BigDecimal outstandingOfAccountsWithPlans();
}
