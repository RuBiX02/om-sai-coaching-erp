package com.omsai.coaching.timetable;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Form of a calendar exception: an institute holiday, a cancelled
 * occurrence or a rescheduled occurrence. The recurring schedule is
 * never touched by an exception.
 */
public class CalendarExceptionForm {

    @NotNull(message = "Please choose the date.")
    private LocalDate exceptionDate;

    @NotNull(message = "Please choose the kind of exception.")
    private CalendarException.Kind kind;

    /** The occurrence to cancel or reschedule (not used for holidays). */
    private Long timetableEntryId;

    private String reason;

    /** The new date of a rescheduled occurrence. */
    private LocalDate rescheduledTo;

    public LocalDate getExceptionDate() {
        return exceptionDate;
    }

    public void setExceptionDate(LocalDate exceptionDate) {
        this.exceptionDate = exceptionDate;
    }

    public CalendarException.Kind getKind() {
        return kind;
    }

    public void setKind(CalendarException.Kind kind) {
        this.kind = kind;
    }

    public Long getTimetableEntryId() {
        return timetableEntryId;
    }

    public void setTimetableEntryId(Long timetableEntryId) {
        this.timetableEntryId = timetableEntryId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDate getRescheduledTo() {
        return rescheduledTo;
    }

    public void setRescheduledTo(LocalDate rescheduledTo) {
        this.rescheduledTo = rescheduledTo;
    }
}
