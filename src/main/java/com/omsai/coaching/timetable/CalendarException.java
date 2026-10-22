package com.omsai.coaching.timetable;

import com.omsai.coaching.common.Auditable;
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

import java.time.LocalDate;

/**
 * A calendar exception: an institute holiday, a cancelled occurrence or
 * a rescheduled occurrence.
 *
 * <p>Exceptions never delete the recurring schedule - a cancelled
 * occurrence leaves its timetable entry untouched, and the exception
 * can be removed again later.</p>
 */
@Entity
@Table(name = "calendar_exceptions")
public class CalendarException extends Auditable {

    public enum Kind {
        /** The institute is closed on that date. */
        HOLIDAY,
        /** One occurrence of a recurring class does not take place. */
        CANCELLED,
        /** One occurrence takes place on another date. */
        RESCHEDULED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_entry_id")
    private TimetableEntry timetableEntry;

    @Column(length = 200)
    private String reason;

    @Column(name = "rescheduled_to")
    private LocalDate rescheduledTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserAccount createdBy;

    public Long getId() {
        return id;
    }

    public LocalDate getExceptionDate() {
        return exceptionDate;
    }

    void setExceptionDate(LocalDate exceptionDate) {
        this.exceptionDate = exceptionDate;
    }

    public Kind getKind() {
        return kind;
    }

    void setKind(Kind kind) {
        this.kind = kind;
    }

    public TimetableEntry getTimetableEntry() {
        return timetableEntry;
    }

    void setTimetableEntry(TimetableEntry timetableEntry) {
        this.timetableEntry = timetableEntry;
    }

    public String getReason() {
        return reason;
    }

    void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDate getRescheduledTo() {
        return rescheduledTo;
    }

    void setRescheduledTo(LocalDate rescheduledTo) {
        this.rescheduledTo = rescheduledTo;
    }

    public UserAccount getCreatedBy() {
        return createdBy;
    }

    void setCreatedBy(UserAccount createdBy) {
        this.createdBy = createdBy;
    }
}
