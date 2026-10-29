-- ---------------------------------------------------------------------
-- Notice board + quiz reviews.
--
--   * an announcement is a notice the institute publishes to the
--     students (everybody, or one batch) from the office screens;
--   * an announcement read marks that one account has seen a notice, so
--     the portal badge can honestly count what a student has not read;
--   * a quiz attempt keeps the letters the student actually answered,
--     so the portal can review a submitted quiz later;
--   * a quiz optionally records the batch-subject assignment it was
--     authored for - the authoring screens pick the subject from the
--     timetable assignments, which also defines the teacher's scope.
-- ---------------------------------------------------------------------

CREATE TABLE announcements (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title        VARCHAR(160) NOT NULL,
    body         TEXT NOT NULL,
    audience     VARCHAR(6) NOT NULL CHECK (audience IN ('ALL', 'BATCH')),
    batch_id     BIGINT REFERENCES batches(id) ON DELETE CASCADE,
    published_by BIGINT NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    active       BOOLEAN NOT NULL DEFAULT true,
    -- ALL notices address everybody; BATCH notices name exactly one batch.
    CONSTRAINT ck_announcements_target
        CHECK ((audience = 'ALL' AND batch_id IS NULL)
            OR (audience = 'BATCH' AND batch_id IS NOT NULL))
);

CREATE INDEX ix_announcements_published ON announcements (active, published_at DESC);
CREATE INDEX ix_announcements_batch ON announcements (batch_id);

CREATE TABLE announcement_reads (
    announcement_id BIGINT NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    user_account_id BIGINT NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    read_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_announcement_reads PRIMARY KEY (announcement_id, user_account_id)
);

-- The review page shows what was answered at submission time.
ALTER TABLE quiz_attempts ADD COLUMN answers TEXT;

-- Every quiz may name the assignment it teaches (nullable: older rows
-- are backfilled below where a matching assignment exists).
ALTER TABLE quizzes
    ADD COLUMN batch_subject_id BIGINT REFERENCES batch_subjects(id) ON DELETE SET NULL;

-- Backfill from the existing batch + subject name pairs.
UPDATE quizzes q
SET batch_subject_id = bs.id
FROM batch_subjects bs
JOIN course_subjects cs ON cs.id = bs.course_subject_id
JOIN subjects s ON s.id = cs.subject_id
WHERE bs.batch_id = q.batch_id
  AND lower(s.name) = lower(q.subject_name)
  AND q.batch_subject_id IS NULL;

CREATE INDEX ix_quizzes_batch_subject ON quizzes (batch_subject_id);
