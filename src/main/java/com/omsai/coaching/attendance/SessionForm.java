package com.omsai.coaching.attendance;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Form backing the new/edit class-session pages.
 */
public class SessionForm {

    @NotNull(message = "Please choose a batch subject.")
    private Long batchSubjectId;

    @NotNull(message = "Class date is required.")
    private LocalDate sessionDate;

    private LocalTime startTime;

    private LocalTime endTime;

    @Size(max = 300, message = "Topic can be at most 300 characters.")
    private String topic;

    @Size(max = 2000, message = "Notes can be at most 2000 characters.")
    private String notes;

    @AssertTrue(message = "End time must be after the start time.")
    public boolean isValidTimeRange() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    public Long getBatchSubjectId() {
        return batchSubjectId;
    }

    public void setBatchSubjectId(Long batchSubjectId) {
        this.batchSubjectId = batchSubjectId;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
