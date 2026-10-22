-- ---------------------------------------------------------------------
-- Examinations and results
--
-- An examination belongs to one batch-subject assignment: that is what
-- makes it a test of one subject for one batch, and it is where the
-- teacher of the exam comes from (the teacher of the assignment).
--
-- A result is stored against the student's ENROLLMENT, not against the
-- student: when a student transfers to another batch, the result stays
-- attached to the enrollment the student actually sat in, so the
-- academic history is preserved.
--
-- Marks are NUMERIC(8,2) - never floats. "Maximum marks" is checked
-- against the recorded mark in the service, because that comparison
-- spans two rows and a CHECK constraint cannot express it.
-- ---------------------------------------------------------------------

CREATE TABLE examinations (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_subject_id BIGINT NOT NULL REFERENCES batch_subjects(id) ON DELETE RESTRICT,
    title            VARCHAR(160) NOT NULL,
    description      TEXT,
    examination_date DATE NOT NULL,
    maximum_marks    NUMERIC(8, 2) NOT NULL CHECK (maximum_marks > 0),
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    published_by     BIGINT REFERENCES user_accounts(id) ON DELETE RESTRICT,
    published_at     TIMESTAMPTZ,
    created_by       BIGINT NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_examinations_batch_subject
    ON examinations (batch_subject_id, examination_date);
CREATE INDEX ix_examinations_date
    ON examinations (examination_date);

CREATE TABLE examination_results (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    examination_id BIGINT NOT NULL REFERENCES examinations(id) ON DELETE RESTRICT,
    enrollment_id  BIGINT NOT NULL REFERENCES enrollments(id) ON DELETE RESTRICT,
    status         VARCHAR(20) NOT NULL
        CHECK (status IN ('PRESENT', 'ABSENT', 'EXCUSED')),
    -- A mark only exists for a present student: an absent or excused
    -- student has NO mark, which is not the same as a mark of zero.
    marks          NUMERIC(8, 2),
    remarks        VARCHAR(400),
    entered_by     BIGINT NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    published      BOOLEAN NOT NULL DEFAULT false,
    published_by   BIGINT REFERENCES user_accounts(id) ON DELETE RESTRICT,
    published_at   TIMESTAMPTZ,
    -- Corrections of a published result are audited, never deleted.
    corrected_by   BIGINT REFERENCES user_accounts(id) ON DELETE RESTRICT,
    corrected_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_examination_results_examination_enrollment
        UNIQUE (examination_id, enrollment_id),
    CONSTRAINT ck_examination_results_marks
        CHECK ((status = 'PRESENT' AND marks IS NOT NULL AND marks >= 0)
            OR (status IN ('ABSENT', 'EXCUSED') AND marks IS NULL))
);

CREATE INDEX ix_examination_results_examination
    ON examination_results (examination_id);
CREATE INDEX ix_examination_results_enrollment
    ON examination_results (enrollment_id);
