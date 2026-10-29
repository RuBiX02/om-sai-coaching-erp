package com.omsai.coaching.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * A short multiple-choice quiz. It belongs to one batch (the demo
 * catalogue quizzes each batch class on its own subjects) and is taken
 * from the student portal.
 */
@Entity
@Table(name = "quizzes")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private com.omsai.coaching.batch.Batch batch;

    @Column(nullable = false, length = 160)
    private String title;

    /** Which subject the questions belong to, shown as a label. */
    @Column(name = "subject_name", nullable = false, length = 60)
    private String subjectName;

    @Column(length = 500)
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public com.omsai.coaching.batch.Batch getBatch() {
        return batch;
    }

    public String getTitle() {
        return title;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setBatch(com.omsai.coaching.batch.Batch batch) {
        this.batch = batch;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
}
