package com.omsai.coaching.student;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.subject.Subject;
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

import java.time.OffsetDateTime;

/**
 * A piece of learning material the student portal can show: notes,
 * worksheets, videos or curated external links.
 *
 * <p>A resource belongs to a subject and, when the material is specific
 * to one class, to a batch. A resource without a batch is institute-wide
 * material for that subject and every batch sees it.</p>
 */
@Entity
@Table(name = "study_resources")
public class StudyResource {

    public enum Type {
        NOTES, WORKSHEET, LINK, VIDEO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private Type resourceType;

    @Column(length = 400)
    private String url;

    @Column(nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Subject getSubject() {
        return subject;
    }

    public Batch getBatch() {
        return batch;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Type getResourceType() {
        return resourceType;
    }

    public String getUrl() {
        return url;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public void setBatch(Batch batch) {
        this.batch = batch;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setResourceType(Type resourceType) {
        this.resourceType = resourceType;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
