package com.omsai.coaching.examination;

import com.omsai.coaching.common.Auditable;
import com.omsai.coaching.enrollment.Enrollment;
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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The result of one student in one examination.
 *
 * <p>The result belongs to the student's <em>enrollment</em>, not to the
 * student: when a student transfers to another batch, the result stays
 * with the enrollment the student actually sat in, so the academic
 * history survives the transfer.</p>
 *
 * <p>An absent or excused student has <em>no</em> mark - that is not
 * the same as a mark of zero, and the database refuses to store one.
 * Published results are corrected, never deleted: the correction is
 * audited with the user and the time.</p>
 */
@Entity
@Table(name = "examination_results",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_examination_results_examination_enrollment",
                columnNames = {"examination_id", "enrollment_id"}))
public class ExaminationResult extends Auditable {

    public enum Status {
        /** The student sat the examination and has a mark. */
        PRESENT,
        /** The student did not sit the examination; no mark. */
        ABSENT,
        /** The student was excused; no mark. */
        EXCUSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "examination_id", nullable = false)
    private Examination examination;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(precision = 8, scale = 2)
    private BigDecimal marks;

    @Column(length = 400)
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entered_by", nullable = false)
    private UserAccount enteredBy;

    @Column(nullable = false)
    private boolean published = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private UserAccount publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** The user who corrected a published result (audit, never a delete). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_by")
    private UserAccount correctedBy;

    @Column(name = "corrected_at")
    private Instant correctedAt;

    public Long getId() {
        return id;
    }

    public Examination getExamination() {
        return examination;
    }

    void setExamination(Examination examination) {
        this.examination = examination;
    }

    public Enrollment getEnrollment() {
        return enrollment;
    }

    void setEnrollment(Enrollment enrollment) {
        this.enrollment = enrollment;
    }

    public Status getStatus() {
        return status;
    }

    void setStatus(Status status) {
        this.status = status;
    }

    public BigDecimal getMarks() {
        return marks;
    }

    void setMarks(BigDecimal marks) {
        this.marks = marks;
    }

    public String getRemarks() {
        return remarks;
    }

    void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public UserAccount getEnteredBy() {
        return enteredBy;
    }

    void setEnteredBy(UserAccount enteredBy) {
        this.enteredBy = enteredBy;
    }

    public boolean isPublished() {
        return published;
    }

    void setPublished(boolean published) {
        this.published = published;
    }

    public UserAccount getPublishedBy() {
        return publishedBy;
    }

    void setPublishedBy(UserAccount publishedBy) {
        this.publishedBy = publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public UserAccount getCorrectedBy() {
        return correctedBy;
    }

    void setCorrectedBy(UserAccount correctedBy) {
        this.correctedBy = correctedBy;
    }

    public Instant getCorrectedAt() {
        return correctedAt;
    }

    void setCorrectedAt(Instant correctedAt) {
        this.correctedAt = correctedAt;
    }

    /** The percentage of the maximum mark, or null when there is no mark. */
    public BigDecimal percentage(BigDecimal maximumMarks) {
        if (marks == null || maximumMarks == null || maximumMarks.signum() <= 0) {
            return null;
        }
        return marks.multiply(new BigDecimal("100"))
                .divide(maximumMarks, 1, java.math.RoundingMode.HALF_UP);
    }
}
