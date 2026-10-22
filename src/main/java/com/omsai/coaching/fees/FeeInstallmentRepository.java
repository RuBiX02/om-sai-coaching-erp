package com.omsai.coaching.fees;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface FeeInstallmentRepository extends JpaRepository<FeeInstallment, Long> {

    /** The schedule of one account, ordered by installment number. */
    @Query("""
            select i from FeeInstallment i
            join fetch i.feeAccount fa
            join fetch fa.student s
            where i.feeAccount.id = :accountId
            order by i.installmentNumber
            """)
    List<FeeInstallment> findForAccount(@Param("accountId") Long accountId);

    /**
     * The schedule in allocation order: earliest due date first, the
     * installment number as the stable tie-breaker.
     */
    @Query("""
            select i from FeeInstallment i
            where i.feeAccount.id = :accountId
            order by i.dueDate, i.installmentNumber
            """)
    List<FeeInstallment> findForAllocation(@Param("accountId") Long accountId);

    boolean existsByFeeAccountId(Long feeAccountId);

    long countByFeeAccountId(Long feeAccountId);

    /** Accounts that have a plan (the fee account stays the obligation). */
    @Query("select count(distinct i.feeAccount.id) from FeeInstallment i")
    long countPlans();

    // ----- reports ---------------------------------------------------------

    /**
     * Installments with a remaining balance whose due date is inside the
     * selected range. The paid amount only counts allocations of
     * RECEIVED payments.
     */
    @Query(value = """
            select i.id as id,
                   i.fee_account_id as feeAccountId,
                   i.installment_number as installmentNumber,
                   i.due_date as dueDate,
                   i.amount as amount,
                   coalesce(paid_sum.paid, 0) as paid,
                   (cast(:today as date) - i.due_date) as daysOverdue,
                   s.full_name as studentName,
                   s.student_code as studentCode,
                   c.name as courseName,
                   fa.academic_year as academicYear
            from fee_installments i
            join fee_accounts fa on fa.id = i.fee_account_id
            join students s on s.id = fa.student_id
            join courses c on c.id = fa.course_id
            left join (select pa.installment_id, sum(pa.amount) as paid
                       from payment_allocations pa
                       join payments p on p.id = pa.payment_id
                       where p.status = 'RECEIVED'
                       group by pa.installment_id) paid_sum on paid_sum.installment_id = i.id
            where (i.amount - coalesce(paid_sum.paid, 0)) > 0
              and (cast(:from as date) is null or i.due_date >= cast(:from as date))
              and (cast(:to as date) is null or i.due_date <= cast(:to as date))
              and (:q = '' or lower(s.full_name) like concat('%', :q, '%')
                            or lower(s.student_code) like concat('%', :q, '%'))
              and (:courseId is null or fa.course_id = :courseId)
              and (:academicYear = '' or fa.academic_year = :academicYear)
            order by i.due_date, i.installment_number, s.full_name
            """,
            countQuery = """
            select count(*)
            from fee_installments i
            join fee_accounts fa on fa.id = i.fee_account_id
            join students s on s.id = fa.student_id
            join courses c on c.id = fa.course_id
            left join (select pa.installment_id, sum(pa.amount) as paid
                       from payment_allocations pa
                       join payments p on p.id = pa.payment_id
                       where p.status = 'RECEIVED'
                       group by pa.installment_id) paid_sum on paid_sum.installment_id = i.id
            where (i.amount - coalesce(paid_sum.paid, 0)) > 0
              and (cast(:from as date) is null or i.due_date >= cast(:from as date))
              and (cast(:to as date) is null or i.due_date <= cast(:to as date))
              and (:q = '' or lower(s.full_name) like concat('%', :q, '%')
                            or lower(s.student_code) like concat('%', :q, '%'))
              and (:courseId is null or fa.course_id = :courseId)
              and (:academicYear = '' or fa.academic_year = :academicYear)
            """,
            nativeQuery = true)
    Page<InstallmentRow> searchUpcoming(@Param("today") LocalDate today,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        @Param("q") String q,
                                        @Param("courseId") Long courseId,
                                        @Param("academicYear") String academicYear,
                                        Pageable pageable);

    /** Installments whose due date has passed and whose balance is positive. */
    @Query(value = """
            select i.id as id,
                   i.fee_account_id as feeAccountId,
                   i.installment_number as installmentNumber,
                   i.due_date as dueDate,
                   i.amount as amount,
                   coalesce(paid_sum.paid, 0) as paid,
                   (cast(:today as date) - i.due_date) as daysOverdue,
                   s.full_name as studentName,
                   s.student_code as studentCode,
                   c.name as courseName,
                   fa.academic_year as academicYear
            from fee_installments i
            join fee_accounts fa on fa.id = i.fee_account_id
            join students s on s.id = fa.student_id
            join courses c on c.id = fa.course_id
            left join (select pa.installment_id, sum(pa.amount) as paid
                       from payment_allocations pa
                       join payments p on p.id = pa.payment_id
                       where p.status = 'RECEIVED'
                       group by pa.installment_id) paid_sum on paid_sum.installment_id = i.id
            where (i.amount - coalesce(paid_sum.paid, 0)) > 0
              and i.due_date < cast(:today as date)
              and (:q = '' or lower(s.full_name) like concat('%', :q, '%')
                            or lower(s.student_code) like concat('%', :q, '%'))
              and (:courseId is null or fa.course_id = :courseId)
              and (:academicYear = '' or fa.academic_year = :academicYear)
            order by i.due_date, i.installment_number, s.full_name
            """,
            countQuery = """
            select count(*)
            from fee_installments i
            join fee_accounts fa on fa.id = i.fee_account_id
            join students s on s.id = fa.student_id
            join courses c on c.id = fa.course_id
            left join (select pa.installment_id, sum(pa.amount) as paid
                       from payment_allocations pa
                       join payments p on p.id = pa.payment_id
                       where p.status = 'RECEIVED'
                       group by pa.installment_id) paid_sum on paid_sum.installment_id = i.id
            where (i.amount - coalesce(paid_sum.paid, 0)) > 0
              and i.due_date < cast(:today as date)
              and (:q = '' or lower(s.full_name) like concat('%', :q, '%')
                            or lower(s.student_code) like concat('%', :q, '%'))
              and (:courseId is null or fa.course_id = :courseId)
              and (:academicYear = '' or fa.academic_year = :academicYear)
            """,
            nativeQuery = true)
    Page<InstallmentRow> searchOverdue(@Param("today") LocalDate today,
                                       @Param("q") String q,
                                       @Param("courseId") Long courseId,
                                       @Param("academicYear") String academicYear,
                                       Pageable pageable);

    /** The next installments due (dashboard list). */
    @Query(value = """
            select i.id as id,
                   i.fee_account_id as feeAccountId,
                   i.installment_number as installmentNumber,
                   i.due_date as dueDate,
                   i.amount as amount,
                   coalesce(paid_sum.paid, 0) as paid,
                   (cast(:today as date) - i.due_date) as daysOverdue,
                   s.full_name as studentName,
                   s.student_code as studentCode,
                   c.name as courseName,
                   fa.academic_year as academicYear
            from fee_installments i
            join fee_accounts fa on fa.id = i.fee_account_id
            join students s on s.id = fa.student_id
            join courses c on c.id = fa.course_id
            left join (select pa.installment_id, sum(pa.amount) as paid
                       from payment_allocations pa
                       join payments p on p.id = pa.payment_id
                       where p.status = 'RECEIVED'
                       group by pa.installment_id) paid_sum on paid_sum.installment_id = i.id
            where (i.amount - coalesce(paid_sum.paid, 0)) > 0
              and i.due_date >= cast(:today as date)
            order by i.due_date, i.installment_number, s.full_name
            limit :limit
            """, nativeQuery = true)
    List<InstallmentRow> nextDue(@Param("today") LocalDate today, @Param("limit") int limit);

    // ----- aggregates ------------------------------------------------------

    /** Total remaining balance of all installments (accounts with a plan). */
    @Query(value = """
            select coalesce(sum(i.amount - coalesce(paid_sum.paid, 0)), 0)
            from fee_installments i
            left join (select pa.installment_id, sum(pa.amount) as paid
                       from payment_allocations pa
                       join payments p on p.id = pa.payment_id
                       where p.status = 'RECEIVED'
                       group by pa.installment_id) paid_sum on paid_sum.installment_id = i.id
            where (i.amount - coalesce(paid_sum.paid, 0)) > 0
            """, nativeQuery = true)
    BigDecimal outstandingWithPlans();

    /** Total remaining balance of the installments that are overdue. */
    @Query(value = """
            select coalesce(sum(i.amount - coalesce(paid_sum.paid, 0)), 0)
            from fee_installments i
            left join (select pa.installment_id, sum(pa.amount) as paid
                       from payment_allocations pa
                       join payments p on p.id = pa.payment_id
                       where p.status = 'RECEIVED'
                       group by pa.installment_id) paid_sum on paid_sum.installment_id = i.id
            where (i.amount - coalesce(paid_sum.paid, 0)) > 0
              and i.due_date < cast(:today as date)
            """, nativeQuery = true)
    BigDecimal overdueBalance(@Param("today") LocalDate today);

    /** Number of installments that are overdue. */
    @Query(value = """
            select count(*)
            from fee_installments i
            left join (select pa.installment_id, sum(pa.amount) as paid
                       from payment_allocations pa
                       join payments p on p.id = pa.payment_id
                       where p.status = 'RECEIVED'
                       group by pa.installment_id) paid_sum on paid_sum.installment_id = i.id
            where (i.amount - coalesce(paid_sum.paid, 0)) > 0
              and i.due_date < cast(:today as date)
            """, nativeQuery = true)
    long countOverdue(@Param("today") LocalDate today);

    /** Remaining amount due inside a date range. */
    @Query(value = """
            select coalesce(sum(i.amount - coalesce(paid_sum.paid, 0)), 0)
            from fee_installments i
            left join (select pa.installment_id, sum(pa.amount) as paid
                       from payment_allocations pa
                       join payments p on p.id = pa.payment_id
                       where p.status = 'RECEIVED'
                       group by pa.installment_id) paid_sum on paid_sum.installment_id = i.id
            where (i.amount - coalesce(paid_sum.paid, 0)) > 0
              and i.due_date >= cast(:from as date)
              and i.due_date <= cast(:to as date)
            """, nativeQuery = true)
    BigDecimal dueWithin(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Number of installments due inside a date range. */
    @Query(value = """
            select count(*)
            from fee_installments i
            left join (select pa.installment_id, sum(pa.amount) as paid
                       from payment_allocations pa
                       join payments p on p.id = pa.payment_id
                       where p.status = 'RECEIVED'
                       group by pa.installment_id) paid_sum on paid_sum.installment_id = i.id
            where (i.amount - coalesce(paid_sum.paid, 0)) > 0
              and i.due_date >= cast(:from as date)
              and i.due_date <= cast(:to as date)
            """, nativeQuery = true)
    long countDueWithin(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** The earliest due date that still carries a balance. */
    @Query(value = """
            select min(i.due_date)
            from fee_installments i
            left join (select pa.installment_id, sum(pa.amount) as paid
                       from payment_allocations pa
                       join payments p on p.id = pa.payment_id
                       where p.status = 'RECEIVED'
                       group by pa.installment_id) paid_sum on paid_sum.installment_id = i.id
            where (i.amount - coalesce(paid_sum.paid, 0)) > 0
              and i.due_date >= cast(:today as date)
            """, nativeQuery = true)
    LocalDate nextDueDate(@Param("today") LocalDate today);
}
