package com.omsai.coaching.fees;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("""
            select p from Payment p
            join fetch p.feeAccount fa
            join fetch fa.student s
            join fetch fa.course c
            join fetch p.receivedBy rb
            left join fetch p.voidedBy vb
            where p.id = :id
            """)
    Optional<Payment> findWithDetails(@Param("id") Long id);

    /**
     * Concurrency-safe receipt number: a single database sequence is
     * the only source; uniqueness is additionally protected by the
     * ux_payments_receipt_number unique index. Never counts rows.
     */
    @Query(value = """
            select :prefix || '-' || lpad(nextval('payment_receipt_seq')::text, 6, '0')
            """, nativeQuery = true)
    String nextReceiptNumber(@Param("prefix") String prefix);

    /** Collections (RECEIVED payments) within a payment-date range. */
    @Query(value = """
            select coalesce(sum(p.amount), 0)
            from payments p
            where p.status = 'RECEIVED'
              and p.payment_date >= :from
              and p.payment_date <= :to
            """, nativeQuery = true)
    BigDecimal receivedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Voided payment totals within a payment-date range (shown separately). */
    @Query(value = """
            select coalesce(sum(p.amount), 0)
            from payments p
            where p.status = 'VOIDED'
              and p.payment_date >= :from
              and p.payment_date <= :to
            """, nativeQuery = true)
    BigDecimal voidedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Collections grouped by payment method within a range. */
    @Query(value = """
            select p.payment_method as method,
                   count(*) as paymentCount,
                   sum(p.amount) as total
            from payments p
            where p.status = 'RECEIVED'
              and p.payment_date >= :from
              and p.payment_date <= :to
            group by p.payment_method
            order by sum(p.amount) desc, p.payment_method
            """, nativeQuery = true)
    List<MethodTotalRow> methodTotalsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Payments of one account with marker and voiding context fetched. */
    @Query("""
            select p from Payment p
            join fetch p.receivedBy rb
            left join fetch p.voidedBy vb
            where p.feeAccount.id = :accountId
            order by p.paymentDate desc, p.id desc
            """)
    List<Payment> findForAccount(@Param("accountId") Long accountId);

    /**
     * The RECEIVED payments of one account in a deterministic order
     * (payment date, creation time, id): used when an installment plan
     * is created and the money that is already in has to be allocated
     * to the new schedule.
     */
    @Query("""
            select p from Payment p
            where p.feeAccount.id = :accountId
              and p.status = 'RECEIVED'
            order by p.paymentDate, p.createdAt, p.id
            """)
    List<Payment> findReceivedForAllocation(@Param("accountId") Long accountId);

    /** Payment search across receipt number, student name and student code. */
    @Query("""
            select p from Payment p
            join fetch p.feeAccount fa
            join fetch fa.student s
            join fetch fa.course c
            join fetch p.receivedBy rb
            where (:q = ''
                   or lower(p.receiptNumber) like concat('%', :q, '%')
                   or lower(s.fullName) like concat('%', :q, '%')
                   or lower(s.studentCode) like concat('%', :q, '%'))
              and (:from is null or p.paymentDate >= :from)
              and (:to is null or p.paymentDate <= :to)
              and (:method is null or p.paymentMethod = :method)
              and (:status is null or p.status = :status)
            order by p.paymentDate desc, p.id desc
            """)
    Page<Payment> search(@Param("q") String q,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("method") Payment.Method method,
                         @Param("status") Payment.Status status,
                         Pageable pageable);
}
