-- ---------------------------------------------------------------------
-- Batch-subject lifecycle
--
-- A batch-subject assignment is what makes a "class": one subject of
-- one course, taught in one batch, optionally by one teacher. It is
-- referenced by class sessions (and through them by attendance), by
-- timetable entries and by examinations, all of which are ON DELETE
-- RESTRICT because their history must never disappear.
--
-- Until now such an assignment could only be deleted, so an assignment
-- that had ever been taught became impossible to remove: the delete
-- failed with a database error the user could not interpret. From now
-- on an assignment can be DEACTIVATED instead of deleted:
--
--   * deactivated assignments disappear from the active pickers, so no
--     NEW class session, timetable entry or examination can be created
--     for them;
--   * the class sessions, attendance marks and results that already
--     exist stay exactly where they are and remain fully readable;
--   * reactivating the assignment puts the subject back into the
--     pickers, keeping the same teacher and the same history.
--
-- Hard deletion stays possible for an assignment that has never been
-- used: the service checks that before deleting anything.
--
-- Existing rows keep their current meaning and stay active.
-- ---------------------------------------------------------------------

ALTER TABLE batch_subjects
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;

CREATE INDEX ix_batch_subjects_active ON batch_subjects (active);
