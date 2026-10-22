package com.omsai.coaching.student;

import com.omsai.coaching.attendance.AttendanceReportService;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.examination.ExaminationResult;
import com.omsai.coaching.examination.ExaminationService;
import com.omsai.coaching.fees.FeeAccount;
import com.omsai.coaching.fees.FeeAccountRepository;
import com.omsai.coaching.fees.InstallmentService;
import com.omsai.coaching.fees.Payment;
import com.omsai.coaching.fees.PaymentRepository;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.timetable.TimetableEntry;
import com.omsai.coaching.timetable.TimetableService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The student portal: the signed-in student's own academic and
 * financial information.
 *
 * <p>Everything is derived from the account that is signed in - there
 * is no student id in the URL, so a student cannot reach another
 * student's records by changing the address. The numbers come from the
 * existing services: the attendance report service, the fee account
 * service and the installment service, so the portal can never show
 * something different from the staff screens.</p>
 */
@Service
public class StudentPortalService {

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceReportService attendanceReportService;
    private final FeeAccountRepository feeAccountRepository;
    private final PaymentRepository paymentRepository;
    private final InstallmentService installmentService;
    private final ExaminationService examinationService;
    private final TimetableService timetableService;

    public StudentPortalService(StudentRepository studentRepository,
                                EnrollmentRepository enrollmentRepository,
                                AttendanceReportService attendanceReportService,
                                FeeAccountRepository feeAccountRepository,
                                PaymentRepository paymentRepository,
                                InstallmentService installmentService,
                                ExaminationService examinationService,
                                TimetableService timetableService) {
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceReportService = attendanceReportService;
        this.feeAccountRepository = feeAccountRepository;
        this.paymentRepository = paymentRepository;
        this.installmentService = installmentService;
        this.examinationService = examinationService;
        this.timetableService = timetableService;
    }

    /** The student profile behind the signed-in account. */
    @Transactional(readOnly = true)
    public Student studentOf(UserAccount account) {
        return studentRepository.findByUserAccountId(account.getId())
                .orElseThrow(() -> new BusinessRuleException(
                        "This account is not linked to a student profile yet."));
    }

    /** The enrollments of the student, newest period first. */
    @Transactional(readOnly = true)
    public List<Enrollment> enrollmentsOf(Student student) {
        return enrollmentRepository.findForStudent(student.getId());
    }

    /** The enrollment the student is currently on (the active one). */
    @Transactional(readOnly = true)
    public Enrollment currentEnrollment(Student student) {
        return enrollmentsOf(student).stream()
                .filter(enrollment -> enrollment.getStatus() == Enrollment.Status.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    // ----- timetable -------------------------------------------------------

    /** The timetable of the batches the student is enrolled in. */
    @Transactional(readOnly = true)
    public List<TimetableEntry> timetableOf(Student student) {
        List<TimetableEntry> entries = new ArrayList<>();
        for (Enrollment enrollment : enrollmentsOf(student)) {
            for (TimetableEntry entry : timetableService.forBatch(enrollment.getBatch().getId())) {
                if (entry.isActive()) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    /** The timetable of one day (for the daily view). */
    @Transactional(readOnly = true)
    public List<TimetableEntry> timetableOn(Student student, LocalDate date) {
        return timetableOf(student).stream()
                .filter(entry -> entry.getDayOfWeek() == date.getDayOfWeek()
                        && entry.appliesOn(date))
                .sorted(java.util.Comparator.comparing(TimetableEntry::getStartTime))
                .toList();
    }

    // ----- attendance ------------------------------------------------------

    /** The attendance report of the student (the same one the staff see). */
    @Transactional(readOnly = true)
    public AttendanceReportService.StudentReport attendanceOf(Student student) {
        return attendanceReportService.forStudent(student.getId(), null, null);
    }

    // ----- fees ------------------------------------------------------------

    /** The fee agreements of the student (one per course and year). */
    @Transactional(readOnly = true)
    public List<FeeAccount> feeAccountsOf(Student student) {
        return feeAccountRepository.findByStudentId(student.getId());
    }

    /** The payments of one fee agreement. */
    @Transactional(readOnly = true)
    public List<Payment> paymentsOf(FeeAccount account) {
        return paymentRepository.findForAccount(account.getId());
    }

    /** The installment plan of one fee agreement, with the derived balances. */
    @Transactional(readOnly = true)
    public List<InstallmentService.InstallmentView> installmentsOf(FeeAccount account) {
        return installmentService.viewFor(account.getId());
    }

    /** The outstanding balance of a fee agreement. */
    @Transactional(readOnly = true)
    public BigDecimal outstandingOf(FeeAccount account) {
        return account.netFee().subtract(
                feeAccountRepository.receivedTotal(account.getId()));
    }

    // ----- results ----------------------------------------------------------

    /** The published results of the student (drafts stay hidden). */
    @Transactional(readOnly = true)
    public List<ExaminationResult> publishedResults(Student student) {
        List<ExaminationResult> results = new ArrayList<>();
        for (Enrollment enrollment : enrollmentsOf(student)) {
            results.addAll(examinationService.publishedResultsFor(enrollment.getId()));
        }
        return results;
    }

    // ----- profile ----------------------------------------------------------

    /** Updates the fields a student may change themselves. */
    @Transactional
    public void updateProfile(Student student, String phone, String parentName,
                              String parentPhone, String address) {
        Student managed = studentRepository.findById(student.getId())
                .orElseThrow(() -> new BusinessRuleException("Student not found."));
        if (phone != null && !phone.isBlank()) {
            managed.setPhone(phone.trim());
        }
        if (parentName != null && !parentName.isBlank()) {
            managed.setParentName(parentName.trim());
        }
        if (parentPhone != null && !parentPhone.isBlank()) {
            managed.setParentPhone(parentPhone.trim());
        }
        if (address != null && !address.isBlank()) {
            managed.setAddress(address.trim());
        }
        studentRepository.save(managed);
    }
}
