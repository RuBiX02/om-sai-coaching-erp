-- ---------------------------------------------------------------------
-- Student portal: the STUDENT role and the account link
--
-- A student portal needs a login. The link is one-to-one and enforced
-- by the database:
--   * students.user_account_id is UNIQUE - one login account can be
--     linked to at most one student profile;
--   * the unique index on teachers.user_id already used for the teacher
--     role is mirrored here, so one student profile cannot be linked to
--     two accounts either.
--
-- The role CHECK of V1 has to allow the new role, so the constraint is
-- replaced (the table, its rows and its history stay untouched).
-- ---------------------------------------------------------------------

-- The role CHECK of V1 is an unnamed inline constraint, which PostgreSQL
-- calls user_accounts_role_check; it has to allow the new role, so the
-- constraint is replaced (the table, its rows and its history stay
-- untouched).
ALTER TABLE user_accounts DROP CONSTRAINT user_accounts_role_check;
ALTER TABLE user_accounts
    ADD CONSTRAINT ck_user_accounts_role
    CHECK (role IN ('ADMIN', 'TEACHER', 'STUDENT'));

ALTER TABLE students
    ADD COLUMN user_account_id BIGINT REFERENCES user_accounts(id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX ux_students_user_account
    ON students (user_account_id) WHERE user_account_id IS NOT NULL;

CREATE INDEX ix_students_user_account
    ON students (user_account_id);
