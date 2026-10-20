package com.omsai.coaching.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form backing object for subject create/edit.
 */
public class SubjectForm {

    @NotBlank(message = "Subject name is required.")
    @Size(max = 140, message = "Subject name must be at most 140 characters.")
    private String name;

    @Size(max = 2000, message = "Description must be at most 2000 characters.")
    private String description;

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
}
