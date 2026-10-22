package com.omsai.coaching.timetable;

import com.omsai.coaching.batch.BatchSubject;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One planned recurring class: a batch-subject assignment on a
 * weekday, in a classroom, inside an effective period.
 *
 * <p>Only the assignment is referenced - the batch, the course, the
 * subject and the teacher are derived from it, so a schedule can never
 * drift away from the teaching assignments.</p>
 *
 * <p>Deactivating an entry (instead of deleting it) keeps the history
 * of the classes that were generated from it.</p>
 */
@Entity
@Table(name = "timetable_entries")
public class TimetableEntry extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_subject_id", nullable = false)
    private BatchSubject batchSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(length = 60)
    private String classroom;

    @Column(length = 200)
    private String topic;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserAccount createdBy;

    public Long getId() {
        return id;
    }

    public BatchSubject getBatchSubject() {
        return batchSubject;
    }

    void setBatchSubject(BatchSubject batchSubject) {
        this.batchSubject = batchSubject;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getClassroom() {
        return classroom;
    }

    void setClassroom(String classroom) {
        this.classroom = classroom;
    }

    public String getTopic() {
        return topic;
    }

    void setTopic(String topic) {
        this.topic = topic;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveUntil() {
        return effectiveUntil;
    }

    void setEffectiveUntil(LocalDate effectiveUntil) {
        this.effectiveUntil = effectiveUntil;
    }

    public boolean isActive() {
        return active;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    public UserAccount getCreatedBy() {
        return createdBy;
    }

    void setCreatedBy(UserAccount createdBy) {
        this.createdBy = createdBy;
    }

    /** True when the entry applies on the given date. */
    public boolean appliesOn(LocalDate date) {
        if (!active || date == null) {
            return false;
        }
        if (date.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveUntil == null || !date.isAfter(effectiveUntil);
    }
}
