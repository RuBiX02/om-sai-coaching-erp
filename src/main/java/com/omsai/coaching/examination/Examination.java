package com.omsai.coaching.examination;

import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.common.Auditable;
import com.omsai.coaching.security.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An examination of one batch-subject assignment: one subject, one
 * batch, one date and one maximum mark.
 *
 * <p>The assignment carries the batch, the course, the subject and the
 * teacher, so an examination always belongs to a class somebody really
 * teaches.</p>
 *
 * <p>The lifecycle is DRAFT → PUBLISHED, with CANCELLED as the third
 * state. A cancelled examination keeps its results - the history is
 * never deleted.</p>
 */
@Entity
@Table(name = "examinations")
public class Examination extends Auditable {

    public enum Status {
        /** Being prepared: the results are not visible to anybody but the staff. */
        DRAFT,
        /** The results are visible to the students. */
        PUBLISHED,
        /** The examination did not take place; its results are kept. */
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_subject_id", nullable = false)
    private BatchSubject batchSubject;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "examination_date", nullable = false)
    private LocalDate examinationDate;

    @Column(name = "maximum_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal maximumMarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private UserAccount publishedBy;

    @Column(name = "published_at")
    private java.time.Instant publishedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserAccount createdBy;

    public Long getId() {
        return id;
    }

    public BatchSubject getBatchSubject() {
        return batchSubject;
    }

    void setBatchSubject(BatchSubject batchSubject) {
        this.batchSubject = batchSubject;
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

    void setExaminationDate(LocalDate examinationDate) {
        this.examinationDate = examinationDate;
    }

    public BigDecimal getMaximumMarks() {
        return maximumMarks;
    }

    void setMaximumMarks(BigDecimal maximumMarks) {
        this.maximumMarks = maximumMarks;
    }

    public Status getStatus() {
        return status;
    }

    void setStatus(Status status) {
        this.status = status;
    }

    public UserAccount getPublishedBy() {
        return publishedBy;
    }

    void setPublishedBy(UserAccount publishedBy) {
        this.publishedBy = publishedBy;
    }

    public java.time.Instant getPublishedAt() {
        return publishedAt;
    }

    void setPublishedAt(java.time.Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public UserAccount getCreatedBy() {
        return createdBy;
    }

    void setCreatedBy(UserAccount createdBy) {
        this.createdBy = createdBy;
    }
}
