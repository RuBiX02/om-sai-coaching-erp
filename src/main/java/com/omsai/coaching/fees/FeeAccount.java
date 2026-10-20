package com.omsai.coaching.fees;

import com.omsai.coaching.common.Auditable;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A fee agreement for one student in one course for one academic year.
 * The database guarantees at most one agreement per student + course +
 * academic year, so a same-course batch transfer can never silently
 * create a second fee liability.
 *
 * <p>The enrollment reference points at the enrollment the agreement
 * was created for; the transfer service repoints it to the enrollment
 * created by a same-course batch transfer, keeping history (and
 * payments) attached to this one account.</p>
 *
 * <p>Fees and outstanding balances are always computed from the stored
 * NUMERIC values and the RECEIVED payment rows — the status shown to
 * users is derived, never stored as separately editable data.</p>
 */
@Entity
@Table(name = "fee_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_fee_accounts_student_course_year",
                columnNames = {"student_id", "course_id", "academic_year"}))
public class FeeAccount extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Column(name = "academic_year", nullable = false, length = 9)
    private String academicYear;

    @DecimalMin(value = "0.00", message = "Gross fee must not be negative.")
    @Digits(integer = 10, fraction = 2)
    @Column(name = "gross_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossFee;

    @DecimalMin(value = "0.00", message = "Discount must not be negative.")
    @Digits(integer = 10, fraction = 2)
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserAccount createdBy;

    public Long getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    void setStudent(Student student) {
        this.student = student;
    }

    public Course getCourse() {
        return course;
    }

    void setCourse(Course course) {
        this.course = course;
    }

    public Enrollment getEnrollment() {
        return enrollment;
    }

    void setEnrollment(Enrollment enrollment) {
        this.enrollment = enrollment;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public BigDecimal getGrossFee() {
        return grossFee;
    }

    public void setGrossFee(BigDecimal grossFee) {
        this.grossFee = grossFee;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UserAccount getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UserAccount createdBy) {
        this.createdBy = createdBy;
    }

    /** netFee = grossFee - discountAmount. */
    public BigDecimal netFee() {
        return grossFee.subtract(discountAmount);
    }

    /** True when the due date has passed as of the given date and a received amount still leaves an outstanding balance. */
    public boolean isOverdueAsOf(LocalDate today, BigDecimal received) {
        return dueDate != null
                && dueDate.isBefore(today)
                && netFee().subtract(received).compareTo(BigDecimal.ZERO) > 0;
    }
}
