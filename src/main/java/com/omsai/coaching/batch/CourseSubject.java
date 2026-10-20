package com.omsai.coaching.batch;

import com.omsai.coaching.common.Auditable;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.subject.Subject;
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

import java.time.Instant;

/**
 * Links a course with one of its subjects. A course-subject pair is
 * unique; batch subject assignments reference this row.
 */
@Entity
@Table(name = "course_subjects",
        uniqueConstraints = @UniqueConstraint(name = "ux_course_subjects_course_subject",
                columnNames = {"course_id", "subject_id"}))
public class CourseSubject extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public Instant getCreatedAt() {
        return super.getCreatedAt();
    }
}
