-- ---------------------------------------------------------------------
-- Subject lifecycle
--
-- A subject (for example "Mathematics") is reusable: it can belong to
-- several courses, and a course-subject pair can be taught in any
-- number of batches. That means a subject that has been used carries
-- history - the course assignments, the batches and the classes that
-- were taught from them.
--
-- Until now a subject could only be created, renamed and described:
-- there was no way to take an obsolete subject out of the offer, and
-- no way at all to remove one that was never used.
--
-- From now on a subject can be DEACTIVATED:
--
--   * a deactivated subject keeps every course relationship and every
--     historical record - nothing is cascaded away;
--   * it no longer appears in the pickers of the course pages, so it
--     cannot be assigned to a new course;
--   * the courses that already include it keep showing it, so the
--     history stays readable;
--   * reactivating it puts it back into the pickers without touching
--     a single relationship.
--
-- Hard deletion stays possible for a subject that was never assigned
-- to any course: the service checks that before deleting anything, and
-- the ON DELETE RESTRICT foreign key of course_subjects remains the
-- final guard.
--
-- Existing rows keep their current meaning and stay active.
-- ---------------------------------------------------------------------

ALTER TABLE subjects
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;

CREATE INDEX ix_subjects_active ON subjects (active);
