package com.omsai.coaching.fees;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    /** The allocations of one account, with payment and installment fetched. */
    @Query("""
            select pa from PaymentAllocation pa
            join fetch pa.payment p
            join fetch pa.installment i
            where p.feeAccount.id = :accountId
            order by i.dueDate, i.installmentNumber, p.paymentDate, p.id
            """)
    List<PaymentAllocation> findForAccount(@Param("accountId") Long accountId);

    /** The allocations of one payment (audit view of what it paid). */
    @Query("""
            select pa from PaymentAllocation pa
            join fetch pa.installment i
            where pa.payment.id = :paymentId
            order by i.dueDate, i.installmentNumber
            """)
    List<PaymentAllocation> findForPayment(@Param("paymentId") Long paymentId);

    /** The amount a payment is split over (always equals the payment amount). */
    @Query("select coalesce(sum(pa.amount), 0) from PaymentAllocation pa where pa.payment.id = :paymentId")
    BigDecimal allocatedTotal(@Param("paymentId") Long paymentId);

    /**
     * How many allocation rows the account has ever had.
     *
     * <p>This counts <em>every</em> allocation, including the ones of a
     * payment that was voided afterwards: a voided payment keeps its
     * allocations as audit history, and that history is exactly what
     * makes an installment schedule immutable. Only the
     * <em>effective</em> paid amount (see {@link #effectivePaid}) ignores
     * voided payments.</p>
     */
    @Query("select count(pa) from PaymentAllocation pa where pa.feeAccountId = :accountId")
    long countForAccount(@Param("accountId") Long accountId);

    /** The effective amount paid against one installment (RECEIVED payments only). */
    @Query(value = """
            select coalesce(sum(pa.amount), 0)
            from payment_allocations pa
            join payments p on p.id = pa.payment_id
            where pa.installment_id = :installmentId
              and p.status = 'RECEIVED'
            """, nativeQuery = true)
    BigDecimal effectivePaid(@Param("installmentId") Long installmentId);
}
