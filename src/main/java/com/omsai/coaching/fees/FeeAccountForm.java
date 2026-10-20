package com.omsai.coaching.fees;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Form backing the fee-agreement pages. Only the enrollment id links
 * the agreement to student/course context — the service derives
 * student and course from that enrollment, so the agreement can never
 * point at a mismatched combination.
 */
public class FeeAccountForm {

    @NotNull(message = "Choose the enrollment this agreement belongs to.")
    private Long enrollmentId;

    @NotNull(message = "Gross fee is required.")
    @DecimalMin(value = "0.00", message = "Gross fee must not be negative.")
    @Digits(integer = 10, fraction = 2, message = "Gross fee can have at most 10 digits and 2 decimals.")
    private BigDecimal grossFee;

    @DecimalMin(value = "0.00", message = "Discount must not be negative.")
    @Digits(integer = 10, fraction = 2, message = "Discount can have at most 10 digits and 2 decimals.")
    private BigDecimal discountAmount;

    private String academicYear;

    private LocalDate dueDate;

    private String notes;

    public Long getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Long enrollmentId) {
        this.enrollmentId = enrollmentId;
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

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
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
}
