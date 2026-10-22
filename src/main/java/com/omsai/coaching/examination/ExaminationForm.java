package com.omsai.coaching.examination;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Form of an examination. The class (batch subject) carries the batch,
 * the course, the subject and the teacher, so they can never disagree
 * with the teaching assignments.
 */
public class ExaminationForm {

    @NotNull(message = "Please choose the class (batch subject).")
    private Long batchSubjectId;

    @NotNull(message = "The title is required.")
    @Size(max = 160, message = "The title can be at most 160 characters.")
    private String title;

    @Size(max = 2000, message = "The description can be at most 2000 characters.")
    private String description;

    @NotNull(message = "The examination date is required.")
    private LocalDate examinationDate;

    @NotNull(message = "The maximum marks are required.")
    @DecimalMin(value = "0.01", message = "The maximum marks must be greater than zero.")
    @Digits(integer = 6, fraction = 2, message = "The maximum marks must have at most two decimal places.")
    private BigDecimal maximumMarks;

    public Long getBatchSubjectId() {
        return batchSubjectId;
    }

    public void setBatchSubjectId(Long batchSubjectId) {
        this.batchSubjectId = batchSubjectId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getExaminationDate() {
        return examinationDate;
    }

    public void setExaminationDate(LocalDate examinationDate) {
        this.examinationDate = examinationDate;
    }

    public BigDecimal getMaximumMarks() {
        return maximumMarks;
    }

    public void setMaximumMarks(BigDecimal maximumMarks) {
        this.maximumMarks = maximumMarks;
    }
}
