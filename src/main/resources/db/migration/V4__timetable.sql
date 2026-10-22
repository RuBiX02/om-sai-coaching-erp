-- ---------------------------------------------------------------------
-- Timetable
--
-- A timetable entry is a PLANNED recurring class: one batch-subject
-- assignment on one weekday, in one classroom, inside an effective
-- period. The class session (attendance) stays the record of what
-- actually happened - a session is one occurrence of a timetable entry.
--
-- The teacher, the batch, the course and the subject are never stored
-- separately: they are derived from the referenced batch-subject
-- assignment, which keeps the schedule consistent with the teaching
-- assignments (and with the teacher-assignment model).
--
-- Times are stored as TIME, days as the name of the weekday, and the
-- effective period as two dates. Two schedules conflict when they are
-- on the same weekday, their time ranges overlap (a schedule ending
-- exactly when another begins does NOT overlap) and their effective
-- periods overlap; the check itself lives in TimetableService, which
-- can see the teacher, the batch and the classroom of every entry.
-- ---------------------------------------------------------------------

CREATE TABLE timetable_entries (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_subject_id BIGINT NOT NULL REFERENCES batch_subjects(id) ON DELETE RESTRICT,
    day_of_week      VARCHAR(10) NOT NULL
        CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
                                'FRIDAY', 'SATURDAY', 'SUNDAY')),
    start_time       TIME NOT NULL,
    end_time         TIME NOT NULL,
    classroom        VARCHAR(60),
    topic            VARCHAR(200),
    effective_from   DATE NOT NULL,
    effective_until  DATE,
    active           BOOLEAN NOT NULL DEFAULT true,
    created_by       BIGINT NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_timetable_entries_time_range
        CHECK (end_time > start_time),
    CONSTRAINT ck_timetable_entries_effective_range
        CHECK (effective_until IS NULL OR effective_until >= effective_from)
);

CREATE INDEX ix_timetable_entries_batch_subject
    ON timetable_entries (batch_subject_id, day_of_week);
CREATE INDEX ix_timetable_entries_day
    ON timetable_entries (day_of_week, active);
CREATE INDEX ix_timetable_entries_classroom
    ON timetable_entries (classroom, day_of_week);

-- ---------------------------------------------------------------------
-- Calendar exceptions
--
-- Holidays, cancelled occurrences and rescheduled occurrences. They
-- never delete the recurring schedule: a cancelled occurrence leaves
-- the timetable entry untouched, and the exception can be removed again
-- later. A holiday applies to the whole institute (no entry), a
-- cancellation or a rescheduling applies to one occurrence.
-- ---------------------------------------------------------------------

CREATE TABLE calendar_exceptions (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exception_date     DATE NOT NULL,
    kind               VARCHAR(20) NOT NULL
        CHECK (kind IN ('HOLIDAY', 'CANCELLED', 'RESCHEDULED')),
    timetable_entry_id BIGINT REFERENCES timetable_entries(id) ON DELETE RESTRICT,
    reason             VARCHAR(200),
    rescheduled_to     DATE,
    created_by         BIGINT NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A rescheduled occurrence must state its new date; a holiday or a
    -- cancellation must not have one.
    CONSTRAINT ck_calendar_exceptions_reschedule
        CHECK ((kind = 'RESCHEDULED' AND rescheduled_to IS NOT NULL)
            OR (kind <> 'RESCHEDULED' AND rescheduled_to IS NULL)),
    -- A cancellation belongs to one occurrence, a holiday to the whole
    -- institute.
    CONSTRAINT ck_calendar_exceptions_scope
        CHECK ((kind = 'HOLIDAY' AND timetable_entry_id IS NULL)
            OR (kind <> 'HOLIDAY' AND timetable_entry_id IS NOT NULL))
);

CREATE INDEX ix_calendar_exceptions_date
    ON calendar_exceptions (exception_date);
CREATE INDEX ix_calendar_exceptions_entry
    ON calendar_exceptions (timetable_entry_id, exception_date);
