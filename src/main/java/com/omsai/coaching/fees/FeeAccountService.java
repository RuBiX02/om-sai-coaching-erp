package com.omsai.coaching.fees;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.enrollment.EnrollmentTransferObserver;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.student.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Fee-agreement business rules. One agreement exists per student +
 * course + academic year (enforced by the database too), so a
 * same-course batch transfer can never create a second liability: the
 * transfer keeps the agreement and repoints it to the new enrollment.
 */
@Service
public class FeeAccountService implements EnrollmentTransferObserver {

    private static final Pattern ACADEMIC_YEAR = Pattern.compile("^[0-9]{4}-[0-9]{2}$");

    private final FeeAccountRepository feeAccountRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final BusinessClock businessClock;

    public FeeAccountService(FeeAccountRepository feeAccountRepository,
                             EnrollmentRepository enrollmentRepository,
                             BusinessClock businessClock) {
        this.feeAccountRepository = feeAccountRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.businessClock = businessClock;
    }

    /**
     * Creates a fee agreement for an ACTIVE enrollment. The student and
     * course are always taken from that enrollment, so the agreement
     * can never refer to a mismatched combination. A duplicate
     * (student, course, academic year) agreement is rejected.
     */
    @Transactional
    public FeeAccount create(FeeAccountForm form, UserAccount admin) {
        Enrollment enrollment = enrollmentRepository.findWithDetails(form.getEnrollmentId())
                .orElseThrow(() -> new BusinessRuleException("Enrollment not found."));
        if (enrollment.getStatus() != Enrollment.Status.ACTIVE) {
            throw new BusinessRuleException(
                    "A fee agreement can only be created for an ACTIVE enrollment (this one is "
                            + enrollment.getStatus() + ").");
        }
        Student student = enrollment.getStudent();
        Course course = enrollment.getCourse();
        String year = form.getAcademicYear() == null || form.getAcademicYear().isBlank()
                ? defaultYear(enrollment)
                : form.getAcademicYear().trim();
        if (!ACADEMIC_YEAR.matcher(year).matches()) {
            throw new BusinessRuleException("Academic year must use the format 2026-27.");
        }
        if (feeAccountRepository.existsByStudentIdAndCourseIdAndAcademicYear(
                student.getId(), course.getId(), year)) {
            throw new BusinessRuleException(
                    student.getFullName() + " already has a fee agreement for \"" + course.getName()
                            + "\" in academic year " + year + ".");
        }
        BigDecimal gross = form.getGrossFee().setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal discount = form.getDiscountAmount() == null
                ? BigDecimal.ZERO
                : form.getDiscountAmount().setScale(2, java.math.RoundingMode.HALF_UP);
        if (discount.compareTo(gross) > 0) {
            throw new BusinessRuleException("Discount cannot exceed the gross fee.");
        }

        FeeAccount account = new FeeAccount();
        account.setStudent(student);
        account.setCourse(course);
        account.setEnrollment(enrollment);
        account.setAcademicYear(year);
        account.setGrossFee(gross);
        account.setDiscountAmount(discount);
        account.setDueDate(form.getDueDate());
        account.setNotes(form.getNotes());
        account.setCreatedBy(admin);
        return feeAccountRepository.save(account);
    }

    /**
     * Edits an agreement. Once payments exist, fee amounts cannot be
     * changed anymore (historical terms must not be silently
     * rewritten); only the due date and notes remain editable.
     */
    @Transactional
    public void update(Long id, FeeAccountForm form) {
        FeeAccount account = feeAccountRepository.findWithDetails(id)
                .orElseThrow(() -> new BusinessRuleException("Fee agreement not found."));
        boolean hasPayments = feeAccountRepository.receivedTotal(id)
                .compareTo(BigDecimal.ZERO) > 0;
        BigDecimal gross = form.getGrossFee().setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal discount = form.getDiscountAmount() == null
                ? BigDecimal.ZERO
                : form.getDiscountAmount().setScale(2, java.math.RoundingMode.HALF_UP);
        if (discount.compareTo(gross) > 0) {
            throw new BusinessRuleException("Discount cannot exceed the gross fee.");
        }
        if (hasPayments) {
            boolean termsChanged = gross.compareTo(account.getGrossFee()) != 0
                    || discount.compareTo(account.getDiscountAmount()) != 0;
            if (termsChanged) {
                throw new BusinessRuleException(
                        "Payments have already been recorded against this agreement, so fee amounts can no longer be changed.");
            }
        }
        account.setGrossFee(gross);
        account.setDiscountAmount(discount);
        account.setDueDate(form.getDueDate());
        account.setNotes(form.getNotes());
        feeAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public FeeAccount getWithDetails(Long id) {
        return feeAccountRepository.findWithDetails(id)
                .orElseThrow(() -> new BusinessRuleException("Fee agreement not found."));
    }

    /** The computed outstanding balance of an account: net fee minus RECEIVED payments. */
    @Transactional(readOnly = true)
    public BigDecimal outstandingOf(FeeAccount account) {
        return account.netFee().subtract(feeAccountRepository.receivedTotal(account.getId()));
    }

    /** One of the three listing modes: 0 all, 1 outstanding, 2 overdue. */
    @Transactional(readOnly = true)
    public Page<FeeAccountRow> search(int mode, Long courseId, String academicYear, String q, Pageable pageable) {
        return feeAccountRepository.search(mode, businessClock.today(), courseId,
                academicYear == null ? "" : academicYear.trim(),
                q == null ? "" : q.trim().toLowerCase(),
                pageable);
    }

    /**
     * Keeps an existing fee agreement attached to the new enrollment
     * created by a same-course batch transfer, inside the transfer
     * transaction. This is what prevents the transfer from charging
     * the student twice for the same course and academic year. A
     * later, independent enrollment (different academic year) is not
     * affected and may receive its own agreement.
     */
    @Override
    public void onTransfer(Student student, Course course, Batch targetBatch, Enrollment newEnrollment) {
        feeAccountRepository
                .findByStudentIdAndCourseIdAndAcademicYear(student.getId(), course.getId(), targetBatch.getAcademicYear())
                .ifPresent(account -> account.setEnrollment(newEnrollment));
    }

    private String defaultYear(Enrollment enrollment) {
        String batchYear = enrollment.getBatch().getAcademicYear();
        if (batchYear != null && ACADEMIC_YEAR.matcher(batchYear).matches()) {
            return batchYear;
        }
        int y = businessClock.today().getYear();
        return y + "-" + String.format("%02d", (y + 1) % 100);
    }
}
