-- ---------------------------------------------------------------------
-- Student learning pack: study resources, quizzes and quiz attempts
--
-- The portal becomes more than a mirror of the office records: a
-- student also STUDIES here. Three small tables:
--
--   * a study resource is a piece of learning material (notes, a
--     worksheet, an external link, ...) tied to a subject and, where
--     relevant, to one batch;
--   * a quiz is a short MCQ test for one batch (a batch quizzed across
--     its subjects keeps the questions curriculum-relevant);
--   * a quiz attempt is one student's submitted answers with the score
--     computed SERVER-SIDE. The browser never sends a score.
--
-- Everything is deleted with its parents (a quiz attempt without the
-- quiz it belongs to is meaningless; resources follow their batch) so
-- the tables are safe to wipe with the demo database.
-- ---------------------------------------------------------------------

CREATE TABLE study_resources (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_id   BIGINT REFERENCES subjects(id) ON DELETE RESTRICT,
    batch_id     BIGINT REFERENCES batches(id) ON DELETE CASCADE,
    title        VARCHAR(160) NOT NULL,
    description  VARCHAR(500),
    resource_type VARCHAR(20) NOT NULL
        CHECK (resource_type IN ('NOTES', 'WORKSHEET', 'LINK', 'VIDEO')),
    url          VARCHAR(400),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- LINK and VIDEO resources must state where they point.
    CONSTRAINT ck_study_resources_link_url
        CHECK ((resource_type IN ('LINK', 'VIDEO') AND url IS NOT NULL)
            OR (resource_type NOT IN ('LINK', 'VIDEO'))),
    CONSTRAINT ck_study_resources_scope
        CHECK (subject_id IS NOT NULL OR batch_id IS NOT NULL)
);

CREATE INDEX ix_study_resources_subject ON study_resources (subject_id, title);
CREATE INDEX ix_study_resources_batch ON study_resources (batch_id);

CREATE TABLE quizzes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id    BIGINT REFERENCES batches(id) ON DELETE CASCADE,
    title       VARCHAR(160) NOT NULL,
    subject_name VARCHAR(60) NOT NULL,
    description VARCHAR(500),
    duration_minutes INT NOT NULL DEFAULT 10
        CHECK (duration_minutes BETWEEN 1 AND 120),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_quizzes_scope CHECK (batch_id IS NOT NULL)
);

CREATE INDEX ix_quizzes_batch ON quizzes (batch_id, title);

CREATE TABLE quiz_questions (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quiz_id        BIGINT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    position       INT NOT NULL CHECK (position BETWEEN 1 AND 50),
    question_text  VARCHAR(600) NOT NULL,
    option_a       VARCHAR(300) NOT NULL,
    option_b       VARCHAR(300) NOT NULL,
    option_c       VARCHAR(300) NOT NULL,
    option_d       VARCHAR(300) NOT NULL,
    correct_option VARCHAR(1) NOT NULL CHECK (correct_option IN ('A', 'B', 'C', 'D')),
    CONSTRAINT uq_quiz_questions_position UNIQUE (quiz_id, position)
);

CREATE INDEX ix_quiz_questions_quiz ON quiz_questions (quiz_id, position);

CREATE TABLE quiz_attempts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quiz_id         BIGINT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    student_id      BIGINT NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    user_account_id BIGINT NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    correct_count   INT NOT NULL CHECK (correct_count >= 0),
    total_count     INT NOT NULL CHECK (total_count > 0),
    CONSTRAINT ck_quiz_attempts_score CHECK (correct_count <= total_count),
    CONSTRAINT uq_quiz_attempts_one UNIQUE (quiz_id, student_id)
);

CREATE INDEX ix_quiz_attempts_student ON quiz_attempts (student_id, submitted_at DESC);
