-- =====================================================================
-- Om Sai Coaching ERP - Phase 2: attendance and fee management
-- PostgreSQL
--
-- Conventions follow V1__init.sql (snake_case, ck_/ux_/ix_ prefixes).
-- All foreign keys are ON DELETE RESTRICT (or defaults): attendance,
-- payment and enrollment history are never automatically destroyed.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Enrollment effective period
--
-- Phase 1 enrollments are append-only history rows, but a closed
-- (COMPLETED/WITHDRAWN) row did not carry the date on which it ended,
-- so attendance eligibility (was the student on the roster on a given
-- date?) could not be derived reliably. Add end_date and backfill the
-- historical rows with the most reliable available data: the row was
-- last written when its status was set, so the business-timezone day of
-- updated_at marks the day of closure.
-- ---------------------------------------------------------------------

ALTER TABLE enrollments ADD COLUMN end_date DATE;

-- Documented backfill rule: closed enrollments end on the
-- Asia/Kolkata (business timezone) calendar day of their last update.
UPDATE enrollments
SET end_date = (updated_at AT TIME ZONE 'Asia/Kolkata')::date
WHERE status <> 'ACTIVE';

ALTER TABLE enrollments
    ADD CONSTRAINT ck_enrollments_end_date
    CHECK (end_date IS NULL OR end_date >= enrollment_date),
    ADD CONSTRAINT ck_enrollments_closed_has_end
    CHECK (status = 'ACTIVE' OR end_date IS NOT NULL);

CREATE INDEX ix_enrollments_batch_start_end
    ON enrollments (batch_id, enrollment_date, end_date);

-- ---------------------------------------------------------------------
-- Class sessions
--
-- A single class that takes place on one date for one existing
-- batch-subject assignment. NOT a recurring timetable.
-- ---------------------------------------------------------------------

CREATE TABLE class_sessions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_subject_id BIGINT    NOT NULL
        REFERENCES batch_subjects(id) ON DELETE RESTRICT,
    session_date     DATE      NOT NULL,
    start_time       TIME,
    end_time         TIME,
    topic            VARCHAR(300),
    notes            TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED')),
    created_by       BIGINT    NOT NULL
        REFERENCES user_accounts(id) ON DELETE RESTRICT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- When both times are given the class must end after it starts
    -- (both times belong to the same session_date).
    CONSTRAINT ck_class_sessions_time_range
        CHECK (end_time IS NULL OR start_time IS NULL OR end_time > start_time)
);

CREATE INDEX ix_class_sessions_batch_subject_date
    ON class_sessions (batch_subject_id, session_date DESC);
CREATE INDEX ix_class_sessions_date ON class_sessions (session_date);
CREATE INDEX ix_class_sessions_status ON class_sessions (status);

-- ---------------------------------------------------------------------
-- Attendance
--
-- Attendance references the student's ENROLLMENT row (not just the
-- student), so a record stays attached to the historical enrollment
-- under which it was taken, even after the student transfers to another
-- batch. One record per session+enrollment; corrections update the
-- existing row.
-- ---------------------------------------------------------------------

CREATE TABLE attendance (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    class_session_id BIGINT   NOT NULL
        REFERENCES class_sessions(id) ON DELETE RESTRICT,
    enrollment_id   BIGINT    NOT NULL
        REFERENCES enrollments(id) ON DELETE RESTRICT,
    status          VARCHAR(20) NOT NULL
        CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')),
    remarks         VARCHAR(300),
    marked_by       BIGINT    NOT NULL
        REFERENCES user_accounts(id) ON DELETE RESTRICT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_attendance_session_enrollment
        UNIQUE (class_session_id, enrollment_id)
);

CREATE INDEX ix_attendance_enrollment ON attendance (enrollment_id);
CREATE INDEX ix_attendance_session_status
    ON attendance (class_session_id, status);

-- ---------------------------------------------------------------------
-- Fee accounts (fee agreements)
--
-- One agreement per student + course + academic year (enforced in the
-- database). The enrollment link points at the enrollment the agreement
-- was created for; the transfer service repoints it to the new
-- enrollment of a same-course batch transfer, so a transfer never
-- creates a second fee liability for the same course+year.
-- ---------------------------------------------------------------------

CREATE TABLE fee_accounts (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id    BIGINT NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    course_id     BIGINT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    enrollment_id BIGINT NOT NULL REFERENCES enrollments(id) ON DELETE RESTRICT,
    academic_year VARCHAR(9) NOT NULL,
    gross_fee     NUMERIC(12, 2) NOT NULL CHECK (gross_fee >= 0),
    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (discount_amount >= 0),
    due_date      DATE,
    notes         TEXT,
    created_by    BIGINT NOT NULL
        REFERENCES user_accounts(id) ON DELETE RESTRICT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_fee_accounts_discount_range
        CHECK (discount_amount <= gross_fee),
    -- The agreement must match the enrollment's student and course; this
    -- is a cross-row rule that CHECK constraints cannot express in
    -- PostgreSQL (no subqueries in CHECK), so it is enforced by
    -- FeeAccountService on every write.
    CONSTRAINT ux_fee_accounts_student_course_year
        UNIQUE (student_id, course_id, academic_year)
);

CREATE INDEX ix_fee_accounts_course ON fee_accounts (course_id, academic_year);
CREATE INDEX ix_fee_accounts_due_date ON fee_accounts (due_date);
CREATE INDEX ix_fee_accounts_enrollment ON fee_accounts (enrollment_id);

-- ---------------------------------------------------------------------
-- Payments
--
-- Payments are append-only: errors are corrected by voiding, never by
-- editing or deleting. Amounts are NUMERIC(12, 2), never floats.
-- Receipt numbers come from payment_receipt_seq (concurrency-safe), the
-- database unique index is the guarantee; values are never derived by
-- counting rows.
-- ---------------------------------------------------------------------

CREATE SEQUENCE payment_receipt_seq START 1;

CREATE TABLE payments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fee_account_id  BIGINT NOT NULL
        REFERENCES fee_accounts(id) ON DELETE RESTRICT,
    receipt_number   VARCHAR(40) NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    payment_date    DATE   NOT NULL,
    payment_method  VARCHAR(20) NOT NULL
        CHECK (payment_method IN ('CASH', 'UPI', 'BANK_TRANSFER', 'CARD', 'CHEQUE', 'OTHER')),
    reference_number VARCHAR(80),
    remarks         TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN ('RECEIVED', 'VOIDED')),
    received_by     BIGINT NOT NULL
        REFERENCES user_accounts(id) ON DELETE RESTRICT,
    voided_by       BIGINT REFERENCES user_accounts(id) ON DELETE RESTRICT,
    voided_at       TIMESTAMPTZ,
    void_reason     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_payments_receipt_number
        UNIQUE (receipt_number),
    -- A voided payment must state who, when and why; a received payment
    -- carries no void information.
    CONSTRAINT ck_payments_void_audit
        CHECK (status = 'RECEIVED'
            OR (voided_by IS NOT NULL AND voided_at IS NOT NULL
                AND void_reason IS NOT NULL))
);
