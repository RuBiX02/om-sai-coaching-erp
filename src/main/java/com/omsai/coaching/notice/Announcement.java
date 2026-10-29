package com.omsai.coaching.notice;

import com.omsai.coaching.batch.Batch;
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

import java.time.OffsetDateTime;

/**
 * A notice the institute publishes to its students: an exam-form
 * reminder, a room change, a fee deadline. It addresses everybody
 * (audience ALL) or exactly one batch (audience BATCH). A withdrawn
 * notice disappears from the portal but the office keeps its history.
 */
@Entity
@Table(name = "announcements")
public class Announcement {

    public enum Audience {
        /** Every student with a portal account. */
        ALL,
        /** Exactly the students of one batch. */
        BATCH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Audience audience;

    /** Non-null only when the audience is one batch. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "published_by", nullable = false)
    private UserAccount publishedBy;

    @Column(nullable = false, insertable = false, updatable = false)
    private OffsetDateTime publishedAt;

    /** Withdrawn notices stay on the office list but leave the portal. */
    @Column(nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Audience getAudience() {
        return audience;
    }

    public Batch getBatch() {
        return batch;
    }

    public UserAccount getPublishedBy() {
        return publishedBy;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setAudience(Audience audience) {
        this.audience = audience;
    }

    public void setBatch(Batch batch) {
        this.batch = batch;
    }

    public void setPublishedBy(UserAccount publishedBy) {
        this.publishedBy = publishedBy;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
