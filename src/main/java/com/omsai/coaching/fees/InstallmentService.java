package com.omsai.coaching.fees;

import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.security.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Installment plans: the schedule of a fee agreement and the automatic
 * allocation of payments to it.
 *
 * <p>Financial rules:</p>
 * <ul>
 *   <li>the {@link FeeAccount} stays the authoritative record of what
 *       is owed - a plan only divides that net fee, it never adds
 *       liability;</li>
 *   <li>the scheduled amounts must sum to the account's net fee
 *       <em>exactly</em> (decimal comparison, never a rounded
 *       "close enough" and never a silent adjustment);</li>
 *   <li>payments are allocated earliest-due-first, the installment
 *       number being the stable tie-breaker; a payment may cover part
 *       of one installment or parts of several;</li>
 *   <li>a payment is never changed by an allocation - only the link
 *       between payment and installment is recorded;</li>
 *   <li>allocations are append-only: voiding a payment keeps its
 *       allocations (audit history) but they stop counting, because
 *       only allocations of RECEIVED payments are effective;</li>
 *   <li>the sum of the installment balances always equals the
 *       account's outstanding balance while a plan exists;</li>
 *   <li>a plan may be replaced while it carries no allocation history;
 *       once a payment has been booked against it the schedule is part
 *       of the financial record and is kept exactly as it was - this
 *       is true even when that payment was voided afterwards, because
 *       the allocations of a voided payment are kept as history.</li>
 * </ul>
 */
@Service
public class InstallmentService {

    /** At least two installments make a schedule; one is a lump sum. */
    public static final int MINIMUM_INSTALLMENTS = 2;

    /**
     * The explanation shown when a schedule cannot be changed any more:
     * a payment has been booked against it, so the schedule is part of
     * the financial record.
     */
    public static final String IMMUTABLE_PLAN_MESSAGE =
            "This installment schedule cannot be edited because payment-allocation history exists. "
                    + "The original schedule and financial history have been preserved.";

    private final FeeAccountRepository feeAccountRepository;
    private final FeeInstallmentRepository installmentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final PaymentRepository paymentRepository;
    private final BusinessClock businessClock;

    public InstallmentService(FeeAccountRepository feeAccountRepository,
                              FeeInstallmentRepository installmentRepository,
                              PaymentAllocationRepository allocationRepository,
                              PaymentRepository paymentRepository,
                              BusinessClock businessClock) {
        this.feeAccountRepository = feeAccountRepository;
        this.installmentRepository = installmentRepository;
        this.allocationRepository = allocationRepository;
        this.paymentRepository = paymentRepository;
        this.businessClock = businessClock;
    }

    // ----- plan creation ---------------------------------------------------

    /**
     * Creates the installment plan of a fee account.
     *
     * <p>The account row is locked, so a payment recorded at the same
     * moment cannot slip in unallocated. Payments that were received
     * <em>before</em> the plan are allocated to it afterwards, oldest
     * first, so the schedule reflects the money that is already in.
     * The whole operation is one transaction: if anything does not add
     * up, nothing is written.</p>
     */
    @Transactional
    public List<FeeInstallment> createPlan(Long feeAccountId, List<InstallmentInput> inputs, UserAccount admin) {
        FeeAccount account = feeAccountRepository.getForUpdate(feeAccountId)
                .orElseThrow(() -> new BusinessRuleException("Fee agreement not found."));
        if (installmentRepository.existsByFeeAccountId(feeAccountId)) {
            throw new BusinessRuleException("This fee agreement already has an installment plan.");
        }
        List<ParsedRow> rows = parse(inputs);
        BigDecimal netFee = account.netFee();
        BigDecimal scheduled = rows.stream()
                .map(row -> row.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (scheduled.compareTo(netFee) != 0) {
            throw new BusinessRuleException("The scheduled installments (" + scheduled
                    + ") must add up to exactly the net fee (" + netFee + ").");
        }

        List<FeeInstallment> installments = new ArrayList<>();
        for (ParsedRow row : rows) {
            FeeInstallment installment = new FeeInstallment();
            installment.setFeeAccount(account);
            installment.setInstallmentNumber(row.number());
            installment.setDueDate(row.dueDate());
            installment.setAmount(row.amount());
            installment.setCreatedBy(admin);
            installments.add(installmentRepository.save(installment));
        }

        // Payments received before the plan existed are allocated to it
        // now, so the schedule and the balance agree from the start.
        List<Payment> received = paymentRepository.findReceivedForAllocation(feeAccountId);
        if (!received.isEmpty()) {
            allocate(account, installments, received);
        }
        verifyReconciliation(account, installments);
        return installments;
    }

    /**
     * Allocates a freshly recorded payment across the installments of
     * its account. Called inside the payment transaction: if the payment
     * cannot be fully allocated, the whole payment is rolled back.
     */
    @Transactional
    public void allocatePayment(Payment payment) {
        FeeAccount account = payment.getFeeAccount();
        List<FeeInstallment> installments = installmentRepository.findForAllocation(account.getId());
        if (installments.isEmpty()) {
            return; // no plan: the Phase 2 behaviour is unchanged
        }
        allocate(account, installments, List.of(payment));
    }

    /**
     * The allocation policy: oldest due date first (installment number
     * as tie-breaker), filling every installment completely before the
     * next one is touched.
     */
    private void allocate(FeeAccount account, List<FeeInstallment> installments, List<Payment> payments) {
        List<FeeInstallment> ordered = new ArrayList<>(installments);
        ordered.sort(Comparator.comparing(FeeInstallment::getDueDate)
                .thenComparing(FeeInstallment::getInstallmentNumber));
        Map<Long, BigDecimal> paid = effectivePaidByInstallment(account.getId());

        for (Payment payment : payments) {
            BigDecimal left = payment.getAmount();
            for (FeeInstallment installment : ordered) {
                if (left.signum() <= 0) {
                    break;
                }
                BigDecimal balance = installment.getAmount()
                        .subtract(paid.getOrDefault(installment.getId(), BigDecimal.ZERO));
                if (balance.signum() <= 0) {
                    continue;
                }
                BigDecimal part = left.min(balance);
                PaymentAllocation allocation = new PaymentAllocation();
                allocation.setPayment(payment);
                allocation.setInstallment(installment);
                allocation.setFeeAccountId(account.getId());
                allocation.setAmount(part);
                allocationRepository.save(allocation);
                paid.merge(installment.getId(), part, BigDecimal::add);
                left = left.subtract(part);
            }
            if (left.signum() > 0) {
                throw new BusinessRuleException("The payment of " + payment.getAmount()
                        + " cannot be allocated completely to the installment plan.");
            }
        }
    }

    /**
     * The plan must reconcile with the account: the sum of the
     * installment balances equals the account's outstanding balance.
     */
    private void verifyReconciliation(FeeAccount account, List<FeeInstallment> installments) {
        Map<Long, BigDecimal> paid = effectivePaidByInstallment(account.getId());
        BigDecimal remaining = installments.stream()
                .map(installment -> installment.getAmount()
                        .subtract(paid.getOrDefault(installment.getId(), BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = account.netFee()
                .subtract(feeAccountRepository.receivedTotal(account.getId()));
        if (remaining.compareTo(outstanding) != 0) {
            throw new BusinessRuleException("The installment plan does not reconcile with the outstanding balance ("
                    + remaining + " against " + outstanding + ").");
        }
    }

    /** The effective amount paid against each installment (RECEIVED payments only). */
    private Map<Long, BigDecimal> effectivePaidByInstallment(Long accountId) {
        Map<Long, BigDecimal> paid = new HashMap<>();
        for (PaymentAllocation allocation : allocationRepository.findForAccount(accountId)) {
            if (allocation.getPayment().isReceived()) {
                paid.merge(allocation.getInstallment().getId(), allocation.getAmount(), BigDecimal::add);
            }
        }
        return paid;
    }

    // ----- plan editing ----------------------------------------------------

    /**
     * Replaces the installment plan of a fee account.
     *
     * <p>The schedule may only be replaced while it carries no
     * payment-allocation history: as soon as a payment has been booked
     * against an installment, the schedule is part of the financial
     * record and is kept exactly as it was. This is true even when the
     * payment was voided afterwards - the allocations of a voided
     * payment are kept as audit history, so the history still exists
     * and the schedule still has to match it.</p>
     *
     * <p>The operation runs in one transaction:</p>
     * <ol>
     *   <li>the fee-account row is locked (the same pessimistic lock the
     *       payment recording uses), so a payment cannot slip in between
     *       the check and the write;</li>
     *   <li>the allocation history is re-checked <em>inside</em> the lock;</li>
     *   <li>the whole proposed schedule is validated before anything is
     *       written, so an invalid submission changes nothing;</li>
     *   <li>the rows that keep their installment number are updated in
     *       place (their identity, their creator and their creation date
     *       survive), the surplus rows are removed and the missing ones
     *       are inserted - an installment number is never moved, so the
     *       unique key can never be violated;</li>
     *   <li>the result is reconciled against the outstanding balance
     *       before the transaction commits.</li>
     * </ol>
     *
     * <p>The fee agreement itself is never touched: a plan only
     * schedules the agreed net fee, it never changes it.</p>
     */
    @Transactional
    public List<FeeInstallment> replacePlan(Long feeAccountId, List<InstallmentInput> inputs, UserAccount admin) {
        // The lock serializes this operation against a concurrent payment
        // recording on the same account.
        FeeAccount account = feeAccountRepository.getForUpdate(feeAccountId)
                .orElseThrow(() -> new BusinessRuleException("Fee agreement not found."));

        List<FeeInstallment> existing = installmentRepository.findForAccount(feeAccountId);
        if (existing.isEmpty()) {
            throw new BusinessRuleException("This fee agreement has no installment plan to edit.");
        }
        // Re-checked inside the lock: a payment recorded a moment ago
        // would have created allocations, and that makes the schedule
        // immutable.
        if (allocationRepository.countForAccount(feeAccountId) > 0) {
            throw new ImmutablePlanException(IMMUTABLE_PLAN_MESSAGE);
        }

        // Validate the whole proposal before touching anything.
        List<ParsedRow> rows = parse(inputs);
        BigDecimal netFee = account.netFee();
        BigDecimal scheduled = rows.stream()
                .map(ParsedRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (scheduled.compareTo(netFee) != 0) {
            throw new BusinessRuleException("The scheduled installments (" + scheduled
                    + ") must add up to exactly the net fee (" + netFee + ").");
        }

        Map<Integer, FeeInstallment> byNumber = new java.util.HashMap<>();
        for (FeeInstallment installment : existing) {
            byNumber.put(installment.getInstallmentNumber(), installment);
        }

        List<FeeInstallment> result = new ArrayList<>();
        for (ParsedRow row : rows) {
            FeeInstallment installment = byNumber.get(row.number());
            if (installment == null) {
                installment = new FeeInstallment();
                installment.setFeeAccount(account);
                installment.setInstallmentNumber(row.number());
                installment.setCreatedBy(admin);
            }
            installment.setDueDate(row.dueDate());
            installment.setAmount(row.amount());
            result.add(installmentRepository.save(installment));
        }

        // The rows whose number is no longer scheduled are removed.
        Set<Integer> kept = rows.stream().map(ParsedRow::number).collect(java.util.stream.Collectors.toSet());
        for (FeeInstallment installment : existing) {
            if (!kept.contains(installment.getInstallmentNumber())) {
                installmentRepository.delete(installment);
            }
        }

        verifyReconciliation(account, result);
        return result;
    }

    /**
     * True when the schedule of an account may be replaced: it must
     * exist and it must not carry any payment-allocation history.
     */
    @Transactional(readOnly = true)
    public boolean isEditable(Long feeAccountId) {
        if (!installmentRepository.existsByFeeAccountId(feeAccountId)) {
            return false;
        }
        return allocationRepository.countForAccount(feeAccountId) == 0;
    }

    /**
     * The current schedule, ready to be shown in the edit form.
     */
    @Transactional(readOnly = true)
    public List<InstallmentInput> scheduleOf(Long feeAccountId) {
        return installmentRepository.findForAccount(feeAccountId).stream()
                .map(installment -> new InstallmentInput(
                        installment.getAmount().toPlainString(),
                        installment.getDueDate().toString(),
                        String.valueOf(installment.getInstallmentNumber())))
                .toList();
    }

    /** The sum of the scheduled amounts of an account. */
    @Transactional(readOnly = true)
    public BigDecimal scheduledTotal(Long feeAccountId) {
        return installmentRepository.findForAccount(feeAccountId).stream()
                .map(FeeInstallment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ----- views -----------------------------------------------------------

    /** One installment with its derived paid, remaining and status values. */
    public record InstallmentView(FeeInstallment installment, BigDecimal paid, BigDecimal remaining,
                                  InstallmentStatus status) {
    }

    /** The schedule of one account, ready for the fee account page. */
    @Transactional(readOnly = true)
    public List<InstallmentView> viewFor(Long accountId) {
        List<FeeInstallment> installments = installmentRepository.findForAccount(accountId);
        if (installments.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> paid = effectivePaidByInstallment(accountId);
        LocalDate today = businessClock.today();
        return installments.stream()
                .map(installment -> {
                    BigDecimal paidAmount = paid.getOrDefault(installment.getId(), BigDecimal.ZERO);
                    BigDecimal remaining = installment.getAmount().subtract(paidAmount);
                    return new InstallmentView(installment, paidAmount, remaining,
                            InstallmentStatus.of(installment.getDueDate(), installment.getAmount(),
                                    paidAmount, today));
                })
                .toList();
    }

    /** True when the account has an installment plan. */
    @Transactional(readOnly = true)
    public boolean hasPlan(Long accountId) {
        return installmentRepository.existsByFeeAccountId(accountId);
    }

    // ----- reports ---------------------------------------------------------

    /** The derived status of one report row. */
    public InstallmentStatus statusOf(InstallmentRow row, LocalDate today) {
        return InstallmentStatus.of(row.getDueDate(), row.getAmount(), row.getPaid(), today);
    }

    /** The remaining balance of a report row. */
    public BigDecimal remainingOf(InstallmentRow row) {
        return row.getAmount().subtract(row.getPaid());
    }

    @Transactional(readOnly = true)
    public Page<InstallmentRow> upcoming(LocalDate from, LocalDate to, String q, Long courseId,
                                         String academicYear, Pageable pageable) {
        return installmentRepository.searchUpcoming(businessClock.today(), from, to, normalize(q), courseId,
                academicYear == null ? "" : academicYear.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<InstallmentRow> overdue(String q, Long courseId, String academicYear, Pageable pageable) {
        return installmentRepository.searchOverdue(businessClock.today(), normalize(q), courseId,
                academicYear == null ? "" : academicYear.trim(), pageable);
    }

    /** The collection overview of the installment module. */
    public record InstallmentOverview(BigDecimal feeAccountOutstanding,
                                      BigDecimal outstandingWithPlans,
                                      BigDecimal overdueBalance,
                                      long overdueCount,
                                      long dueInSevenDays,
                                      BigDecimal dueInSevenDaysAmount,
                                      long planCount,
                                      LocalDate nextDueDate) {
    }

    @Transactional(readOnly = true)
    public InstallmentOverview overview() {
        LocalDate today = businessClock.today();
        LocalDate inSevenDays = today.plusDays(7);
        return new InstallmentOverview(
                feeAccountRepository.totalOutstanding(),
                installmentRepository.outstandingWithPlans(),
                installmentRepository.overdueBalance(today),
                installmentRepository.countOverdue(today),
                installmentRepository.countDueWithin(today, inSevenDays),
                installmentRepository.dueWithin(today, inSevenDays),
                installmentRepository.countPlans(),
                installmentRepository.nextDueDate(today));
    }

    /** The next installments due (dashboard list). */
    @Transactional(readOnly = true)
    public List<InstallmentRow> nextDue(int limit) {
        return installmentRepository.nextDue(businessClock.today(), limit);
    }

    // ----- form parsing ----------------------------------------------------

    /** One validated row of the plan form. */
    private record ParsedRow(int number, LocalDate dueDate, BigDecimal amount) {
    }

    /**
     * Parses and validates the submitted rows: at least two of them,
     * each with a positive amount of at most two decimal places and a
     * due date, numbered 1..n without gaps.
     */
    private List<ParsedRow> parse(List<InstallmentInput> inputs) {
        List<ParsedRow> parsed = new ArrayList<>();
        int rowNumber = 0;
        for (InstallmentInput input : inputs == null ? List.<InstallmentInput>of() : inputs) {
            rowNumber++;
            if (input == null || input.isEmpty()) {
                continue;
            }
            BigDecimal amount = parseAmount(input.amount(), rowNumber);
            LocalDate dueDate = parseDueDate(input.dueDate(), rowNumber);
            Integer number = parseNumber(input.installmentNumber(), rowNumber);
            parsed.add(new ParsedRow(number, dueDate, amount));
        }
        if (parsed.size() < MINIMUM_INSTALLMENTS) {
            throw new BusinessRuleException("An installment plan needs at least "
                    + MINIMUM_INSTALLMENTS + " installments.");
        }
        parsed.sort(Comparator.comparingInt(ParsedRow::number));
        for (int i = 0; i < parsed.size(); i++) {
            if (parsed.get(i).number() != i + 1) {
                throw new BusinessRuleException(
                        "The installments must be numbered 1 to " + parsed.size() + " without gaps.");
            }
        }
        return parsed;
    }

    private BigDecimal parseAmount(String text, int rowNumber) {
        if (text == null || text.isBlank()) {
            throw new BusinessRuleException("Row " + rowNumber + ": the installment amount is required.");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("Row " + rowNumber + ": \"" + text.trim()
                    + "\" is not a valid amount.");
        }
        if (amount.scale() > 2) {
            throw new BusinessRuleException("Row " + rowNumber
                    + ": the amount must have at most two decimal places.");
        }
        if (amount.signum() <= 0) {
            throw new BusinessRuleException("Row " + rowNumber + ": the amount must be greater than zero.");
        }
        if (amount.precision() - amount.scale() > 10) {
            throw new BusinessRuleException("Row " + rowNumber + ": the amount is too large.");
        }
        return amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    private LocalDate parseDueDate(String text, int rowNumber) {
        if (text == null || text.isBlank()) {
            throw new BusinessRuleException("Row " + rowNumber + ": the due date is required.");
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessRuleException("Row " + rowNumber + ": \"" + text.trim()
                    + "\" is not a valid date (use YYYY-MM-DD).");
        }
    }

    private int parseNumber(String text, int rowNumber) {
        if (text == null || text.isBlank()) {
            throw new BusinessRuleException("Row " + rowNumber + ": the installment number is required.");
        }
        try {
            int number = Integer.parseInt(text.trim());
            if (number < 1) {
                throw new BusinessRuleException("Row " + rowNumber
                        + ": the installment number must be 1 or greater.");
            }
            return number;
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("Row " + rowNumber + ": \"" + text.trim()
                    + "\" is not a valid installment number.");
        }
    }

    private static String normalize(String q) {
        return q == null ? "" : q.trim().toLowerCase();
    }
}
