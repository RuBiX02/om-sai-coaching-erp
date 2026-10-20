package com.omsai.coaching.attendance;

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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Attendance of one enrollment (student-in-batch) for one class
 * session. The record references the student's ENROLLMENT, not just the
 * student, so it stays connected to the historical enrollment even
 * after the student transfers to another batch. One record per
 * session+enrollment; corrections update the existing row (marked_by
 * becomes the user who last marked or corrected it, updated_at the
 * correction time). Attendance rows are never deleted.
 */
@Entity
@Table(name = "attendance",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_attendance_session_enrollment",
                columnNames = {"class_session_id", "enrollment_id"}))
public class Attendance extends Auditable {

    public enum Status {
        PRESENT,
        ABSENT,
        LATE,
        EXCUSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_session_id", nullable = false)
    private ClassSession classSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Size(max = 300)
    @Column(length = 300)
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marked_by", nullable = false)
    private UserAccount markedBy;

    public Long getId() {
        return id;
    }

    public ClassSession getClassSession() {
        return classSession;
    }

    public void setClassSession(ClassSession classSession) {
        this.classSession = classSession;
    }

    public Enrollment getEnrollment() {
        return enrollment;
    }

    public void setEnrollment(Enrollment enrollment) {
        this.enrollment = enrollment;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public UserAccount getMarkedBy() {
        return markedBy;
    }

    public void setMarkedBy(UserAccount markedBy) {
        this.markedBy = markedBy;
    }
}
