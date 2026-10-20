-- =====================================================================
-- Om Sai Coaching ERP - initial schema (Phase 1)
-- PostgreSQL
-- =====================================================================

CREATE TABLE user_accounts (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name     VARCHAR(120)  NOT NULL,
    email         VARCHAR(180)  NOT NULL,
    password_hash VARCHAR(120)  NOT NULL,
    role          VARCHAR(20)   NOT NULL CHECK (role IN ('ADMIN', 'TEACHER')),
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_user_accounts_email ON user_accounts (lower(email));
CREATE INDEX ix_user_accounts_role ON user_accounts (role);

CREATE TABLE students (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_code  VARCHAR(40)   NOT NULL,
    full_name     VARCHAR(120)  NOT NULL,
    phone         VARCHAR(20)   NOT NULL,
    parent_name   VARCHAR(120),
    parent_phone  VARCHAR(20),
    address       VARCHAR(400),
    admission_date DATE         NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_students_code ON students (upper(student_code));
CREATE INDEX ix_students_name ON students (lower(full_name));
CREATE INDEX ix_students_phone ON students (phone);

CREATE SEQUENCE student_code_seq START 1;

CREATE TABLE courses (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(140)  NOT NULL,
    category        VARCHAR(80)   NOT NULL,
    description     TEXT,
    duration_months INTEGER       CHECK (duration_months IS NULL OR duration_months BETWEEN 1 AND 120),
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_courses_name ON courses (lower(name));
CREATE INDEX ix_courses_status ON courses (status);

CREATE TABLE subjects (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(140)  NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_subjects_name ON subjects (lower(name));

-- A course may contain many subjects; a subject may belong to many courses.
CREATE TABLE course_subjects (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id  BIGINT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    subject_id BIGINT NOT NULL REFERENCES subjects(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_course_subjects_course_subject UNIQUE (course_id, subject_id)
);

CREATE INDEX ix_course_subjects_course ON course_subjects (course_id);
CREATE INDEX ix_course_subjects_subject ON course_subjects (subject_id);

CREATE TABLE batches (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id     BIGINT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    name          VARCHAR(120)  NOT NULL,
    academic_year VARCHAR(9)    NOT NULL,
    start_date    DATE          NOT NULL,
    end_date      DATE,
    status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_batches_course_year_name UNIQUE (course_id, academic_year, name),
    CONSTRAINT ck_batches_date_range CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_batches_academic_year CHECK (academic_year ~ '^[0-9]{4}-[0-9]{2}$')
);

CREATE INDEX ix_batches_course ON batches (course_id);
CREATE INDEX ix_batches_status ON batches (status);

CREATE TABLE teachers (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT REFERENCES user_accounts(id) ON DELETE RESTRICT,
    full_name  VARCHAR(120)  NOT NULL,
    phone      VARCHAR(20)   NOT NULL,
    email      VARCHAR(180),
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- One user account may be linked to at most one teacher profile.
CREATE UNIQUE INDEX ux_teachers_user ON teachers (user_id) WHERE user_id IS NOT NULL;

CREATE TABLE batch_subjects (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id          BIGINT NOT NULL REFERENCES batches(id) ON DELETE RESTRICT,
    course_subject_id BIGINT NOT NULL REFERENCES course_subjects(id) ON DELETE RESTRICT,
    teacher_id        BIGINT REFERENCES teachers(id) ON DELETE RESTRICT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A subject can be taught at most once per batch; the teacher per
    -- batch+subject is assigned through the service layer.
    CONSTRAINT ux_batch_subjects_batch_subject UNIQUE (batch_id, course_subject_id)
);

CREATE INDEX ix_batch_subjects_batch ON batch_subjects (batch_id);
CREATE INDEX ix_batch_subjects_teacher ON batch_subjects (teacher_id);

CREATE TABLE enrollments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id      BIGINT NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    batch_id        BIGINT NOT NULL REFERENCES batches(id) ON DELETE RESTRICT,
    course_id       BIGINT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    enrollment_date DATE   NOT NULL,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'WITHDRAWN')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A student may hold at most one ACTIVE enrollment per batch...
CREATE UNIQUE INDEX ux_enrollments_active_student_batch
    ON enrollments (student_id, batch_id) WHERE status = 'ACTIVE';
-- ...and at most one ACTIVE enrollment per course (a change of batch
-- within the same course must be done through a transfer).
CREATE UNIQUE INDEX ux_enrollments_active_student_course
    ON enrollments (student_id, course_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_enrollments_student ON enrollments (student_id);
CREATE INDEX ix_enrollments_batch ON enrollments (batch_id);
