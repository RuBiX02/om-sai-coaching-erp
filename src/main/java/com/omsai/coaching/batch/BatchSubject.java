package com.omsai.coaching.batch;

import com.omsai.coaching.common.Auditable;
import com.omsai.coaching.teacher.Teacher;
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

/**
 * A subject that is being taught in a batch, with the teacher assigned
 * to teach it. The batch's subject must belong to the batch's course -
 * validated in BatchService.
 *
 * <p>This is the entity that makes a "class". Because class sessions,
 * their attendance, timetable entries and examinations all point at it
 * and are never deleted, an assignment that has been used cannot be
 * removed - it is deactivated instead. A deactivated assignment keeps
 * its whole history but no longer appears in the pickers, so no new
 * class can be scheduled for it.</p>
 */
@Entity
@Table(name = "batch_subjects",
        uniqueConstraints = @UniqueConstraint(name = "ux_batch_subjects_batch_subject",
                columnNames = {"batch_id", "course_subject_id"}))
public class BatchSubject extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_subject_id", nullable = false)
    private CourseSubject courseSubject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    /**
     * Whether the subject is currently being taught in this batch.
     * Deactivating an assignment keeps its classes, attendance and
     * results but takes it out of the pickers for new classes.
     */
    @Column(nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public Batch getBatch() {
        return batch;
    }

    public void setBatch(Batch batch) {
        this.batch = batch;
    }

    public CourseSubject getCourseSubject() {
        return courseSubject;
    }

    public void setCourseSubject(CourseSubject courseSubject) {
        this.courseSubject = courseSubject;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
