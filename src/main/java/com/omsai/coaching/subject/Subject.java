package com.omsai.coaching.subject;

import com.omsai.coaching.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * A subject (for example "Mathematics"). Subjects are reusable and can
 * belong to several courses.
 *
 * <p>A subject that is no longer taught can be deactivated instead of
 * deleted: it then disappears from the pickers of the course pages
 * while every course relationship and every historical record stays
 * exactly where it is. A subject that was never assigned to any course
 * may be deleted outright.</p>
 */
@Entity
@Table(name = "subjects")
public class Subject extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 140)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /**
     * Whether the subject is still taught. Deactivating keeps every
     * course relationship but takes the subject out of the pickers, so
     * it cannot be assigned to a new course any more.
     */
    @Column(nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
