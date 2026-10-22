package com.omsai.coaching.timetable;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Form of a recurring timetable entry. The assignment is the only
 * reference: the batch, the course, the subject and the teacher are
 * derived from it, so they can never disagree with the teaching
 * assignments.
 */
public class TimetableForm {

    @NotNull(message = "Please choose the class (batch subject).")
    private Long batchSubjectId;

    @NotNull(message = "Please choose the weekday.")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "The start time is required.")
    private LocalTime startTime;

    @NotNull(message = "The end time is required.")
    private LocalTime endTime;

    private String classroom;

    private String topic;

    @NotNull(message = "The first day of the schedule is required.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveUntil;

    private boolean active = true;

    public Long getBatchSubjectId() {
        return batchSubjectId;
    }

    public void setBatchSubjectId(Long batchSubjectId) {
        this.batchSubjectId = batchSubjectId;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
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

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveUntil() {
        return effectiveUntil;
    }

    public void setEffectiveUntil(LocalDate effectiveUntil) {
        this.effectiveUntil = effectiveUntil;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
