package com.omsai.coaching.course;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form backing object for course create/edit.
 */
public class CourseForm {

    @NotBlank(message = "Course name is required.")
    @Size(max = 140, message = "Course name must be at most 140 characters.")
    private String name;

    @NotBlank(message = "Category is required.")
    @Size(max = 80, message = "Category must be at most 80 characters.")
    private String category;

    @Size(max = 2000, message = "Description must be at most 2000 characters.")
    private String description;

    @Min(value = 1, message = "Duration must be at least 1 month.")
    @Max(value = 120, message = "Duration must be at most 120 months.")
    private Integer durationMonths;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDurationMonths() {
        return durationMonths;
    }

    public void setDurationMonths(Integer durationMonths) {
        this.durationMonths = durationMonths;
    }
}
