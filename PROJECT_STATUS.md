# PROJECT STATUS — Om Sai Coaching ERP, Phase 1

Date: 2026-10-20
Scope: everything listed in the Phase 1 specification that was actually
built and verified, with an honest account of gaps, limitations and the
defects found (and fixed) during development.

---

## 1. Bottom line

The Phase 1 scope is implemented and works end to end:

- **`mvn test`: 98 tests, 0 failures, 0 errors, 0 skipped** (green).
- **E2E smoke suite: 71/71 checks passed** (`zsh scripts/e2e-smoke.sh`
  against a locally running app).
- Both reports above are from real runs on this machine, **not estimates
  from CI**. The exact environment is described in §6 limits.

Everything the app presents to the user is verified by automated checks
or by direct HTTP/DB inspection. What is *not* verified is clearly split
out and listed in §4.

---

## 2. Feature status

### Fully working (verified)

- **Authentication & accounts**: session login by email, BCrypt password
  hashing, CSRF-protected forms, role `ADMIN`, deactivated accounts
  cannot sign in. Verified by JUnit tests and the smoke script.
- **Access control matrix**: anonymous → login redirect; `TEACHER` role →
  read-only `/teacher` home only (every other URL is 403, including POSTs);
  `ADMIN` → all management screens. Verified by JUnit tests and smoke.
- **Server-side security defaults**: no hardcoded credentials; bootstrap
  admin creation is opt-in through environment variables and idempotent
  (JVM-start runner was verified skip-on-existing over real restarts).
- **Courses**: create/edit/deactivate/reactivate, unique (case-insensitive)
  names; listing with search and status filters; per-course subject list.
- **Subjects**: create/edit, unique names; course assignment managed from
  the course page (not the subject page).
- **Batches**: create/edit with course, academic year and dates; unique
  (course, year, name) triple; end date cannot precede start date
  (both `@AssertTrue` in the form and a service-level guard);
  deactivate/reactivate; batch course is immutable after creation.
- **Batch subjects**: only subjects of the batch's course can be attached;
  duplicates rejected (service + DB unique index); a batch subject can be
  removed; its teacher assignment cleared/reassigned.
- **Teacher assignments**: assign/clear an active teacher per batch
  subject; inactive teachers cannot receive new assignments (verified);
  reactivating a teacher restores eligibility.
- **Referential safety**: a course subject that is currently taught in a
  batch cannot be removed from the course; the DB uses ON DELETE RESTRICT
  everywhere so removing referenced rows fails cleanly.
- **Students**: register with a generated unique code `OMS-2026-#####`
  (PostgreSQL sequence; case-insensitive uniqueness enforced twice),
  edit, activate/deactivate, search by name/code/phone.
- **Enrollments**: enroll an active student into an active batch; block
  duplicates (per student+batch and per student+course), block inactive
  students/batches/courses, suggest transfer on same-course conflicts —
  all both in the service and enforced by partial unique indexes at the
  database level (verified directly in tests).
- **Transfers**: batch change inside one course only; the previous ACTIVE
  record is closed COMPLETED and a new ACTIVE row is created for the
  target batch in one transaction (`saveAll` + `flush()` ordering chosen
  deliberately to respect the partial unique constraints during the
  transition). Transfer history is preserved: both rows remain in the
  table (verified by tests and DB assertions).
- **Withdraw / complete**: only from ACTIVE status; both free the
  student so re-enrollment becomes possible.
- **Instructor accounts**: admin creates ADMIN/TEACHER login accounts
  (validation on role, email format, password length), can
  deactivate/reactivate them (the last active admin cannot be
  deactivated) and reset passwords (min 12 chars).
- **Teacher profiles ↔ login accounts**: a TEACHER-role account can be
  linked to a teacher profile (1-to-1, uniqueness enforced); ADMIN-role
  accounts are rejected for linking; already-linked accounts cannot be
  linked twice; unlinking/change works via the edit form.
- **Dashboard (ADMIN)**: live counts (active students, active courses,
  active batches, active teachers) and the 5 most recent enrollments —
  all computed from DB queries, nothing hardcoded.
- **UI**: 24 Thymeleaf templates, server-rendered, Bootstrap 5.3 +
  Bootstrap Icons vendored locally (works without internet access),
  no inline styles/scripts (strict CSP), small progressive-enhancement
  JS for filtering/navigation niceties.
- **Validation feedback**: invalid form posts re-render the form with
  exact field errors (all form cases verified in JUnit + smoke).
- **Error pages**: friendly 403 / 404 / 500 pages instead of whitelabel
  noise; access-denied pages work for both real content and CSRF-denied
  POSTs.

### Working but with documented limits

- **Login rate limiting**: works and verified (5 failures/15 min per
  username+IP, 6th attempt → `/login?locked`, correct-password attempt is
  also blocked once locked, success clears the counter). Limitation: it
  is an in-memory map, so it (a) resets on restart and (b) protects only
  one process — see §4.
- **Teacher read-only home page**: verified working for a teacher with
  and without teaching assignments and without a linked profile; content
  limited to what the spec allows.

### Not implemented (deliberately out of Phase 1 scope)

- No REST API endpoints (Phase 1 is purely server-rendered pages).
- No multiple-branch or multi-campus support, no invoices/payments,
  no attendance tracking, no reports/PDF export.
- No email/SMS notifications, no password "forgot" flow (password resets
  are admin-driven), no user-invite emails.
- No file uploads (photos, documents).
- No bulk import/export (CSV/Excel) yet.
- No dark mode/tailwind-style theming beyond the single Bootstrap theme.

### Not verified

- **Visual/browser rendering**: the development environment here had no
  browser automation tool available, so pixel-level layout was not
  exercised by a real browser. All pages were fetched, parsed and checked
  by automated means (curl over HTTP including CSRF flows, HTML content
  assertions in the smoke script and MockMvc tests), so markup, status
  codes, flash messages, validation messages and role behaviour are
  verified to render correctly — but nothing was confirmed in a graphic
  browser session.
- **Docker/Testcontainers**: unavailable on this machine, so there is no
  "spin up the whole stack in Docker" story yet. Tests run against a
  locally installed PostgreSQL, which requires the two databases from
  README §1 to exist.

---

## 3. Database migrations

- `V1__init.sql` is the single Flyway migration. It creates all tables,
  enforced CHECK constraints, identifier sequences and the partial unique
  indexes that back the business rules (one ACTIVE enrollment per batch
  and per course, unique course/name/email/code, batch-subject uniqueness,
  user-account/teacher profile link uniqueness).
- Hibernate is `ddl-auto: validate`: the schema is Flyway-owned, verified
  at boot. Successful migrations are committed in the same transaction
  as the described schema state — no data mutations were performed by
  migrations, so there is no data-backfill concern in Phase 1.
- Applied against: `omsai` (dev) and `omsai_test` (tests). Verified
  fresh-database startup (empty `omsai_test` at creation of this log).

---

## 4. Defects found during this phase — and fixed

These were real bugs discovered by the smoke suite and tests, not
stylistic complaints. All are fixed in the committed code:

1. **Rate limiter off-by-one** — the very first failed login within a
  fresh window was not counted, giving attackers one extra try per
  window (max attempts + 1). Fixed to count the first failure; covered
  by deterministic unit tests and by the live `6th attempt locked`
  smoke check.
2. **Teacher login could not redirect** — `/` was matched after
  `/teacher/**` and forced the ADMIN requirement, so a teacher arriving
  at `/` received 403 and the role-based `HomeController` branch was
  dead code. Fixed the request-matcher order in `SecurityConfig`;
  verified in MockMvc and smoke tests.
3. **Teacher detail page crashed (500)** — reading
  `teacher.linkedUser.email` hit a Hibernate proxy with
  `LazyInitializationException` after the transaction ended (visible
  only when the profile had a linked account). Fixed with an explicit
  `left join fetch` query (`findWithLinkedUser`) used by
  `TeacherService.get()`.
4. **Batch detail page crashed (500)** — same disease on
  `batch.course` after enrollment/navigation. Fixed with a
  `findWithCourse` join-fetch used by `BatchService.get()`.
5. **Invisible validation error** — the batch form's
  `@AssertTrue isEndDateAfterStart` bound its error to property
  `endDateAfterStart`, which the template did not render, so an invalid
  date range appeared to do nothing. The template now displays it, and
  a web-layer test asserts the changed property is populated.
6. **Thymeleaf "new" word restriction** — Thymeleaf 3.1.5's restricted
  expression evaluator rejects *any* fragment-argument expression that
  contains the standalone word `new` (even inside string literals like
  `'New batch'`), which broke several form pages with
  `TemplateInputException`. Fixed by computing page titles with
  `th:with` and passing plain `${pageTitle}` variables into fragments
  (documented here because it surprises everyone the first time).

---

## 5. Tests actually executed

JUnit suite (`./mvnw test`, run against `omsai_test`):

| Suite                        | Tests | Result |
|------------------------------|-------|--------|
| EnrollmentServiceTest        | 17    | pass   |
| RoleAccessWebTest            | 10    | pass   |
| CourseServiceTest            | 10    | pass   |
| BatchServiceTest             | 11    | pass   |
| ValidationWebTest            | 7     | pass   |
| EnrollmentConstraintTest     | 6     | pass   |
| TeacherServiceTest           | 7     | pass   |
| LoginWebTest                 | 4     | pass   |
| LoginRateLimiterTest         | 5     | pass   |
| UserServiceTest              | 8     | pass   |
| StudentServiceTest           | 5     | pass   |
| SubjectServiceTest           | 5     | pass   |
| DashboardServiceTest         | 2     | pass   |
| CoachingErpApplicationTests  | 1     | pass   |
| **Total**                    | **98**| **0 failures, 0 errors** |

Highlights of what the tests prove:

- Enrollment rule set (duplicate batch/course enrollments, inactive
  student/batch/course, unknown ids, transfer same-course, transfer to
  same batch, cross-course transfer, withdraw/complete, history
  preservation) — EnrollmentServiceTest.
- The DB-level partial unique indexes cannot be bypassed
  (EnrollmentConstraintTest writes invalid rows directly and expects
  `DataIntegrityViolationException`).
- Login, bad-password, unknown-user and lockout behaviors over HTTP,
  including that a correct password is still blocked when locked
  (LoginWebTest).
- Role matrix (anonymous/teacher/admin) over MockMvc with CSRF
  (RoleAccessWebTest).
- Every management form's validation errors re-render with the proper
  field/model errors (ValidationWebTest).
- All service-side business rules (courses, subjects, batches, students,
  teachers, users, dashboard); Spring context boot on a real PostgreSQL.

E2E smoke suite (`zsh scripts/e2e-smoke.sh`, ~71 checks against the truly
running app): real HTTP flows — CSRF token fetches, session cookies,
redirect/flash-message following, DB row assertions through psql —
covering login, role enforcement incl. 403s, every form's happy and
rejection paths, enrollment + transfer + withdraw history invariants,
removal protections, teacher linking, rate-limit lockout, logout session
invalidation and dashboard rendering. Final run: **71 checks, 0 failed.**

---

## 6. Environment & honest limitations

- **Machine specifics used for the runs above**: PostgreSQL 18.4 local
  cluster on port **5433** (databases `omsai` and `omsai_test`, user
  `tojo`/`omsai_dev` in `application.yml` defaults), Java **25**
  (`/usr/lib/jvm/java-25-openjdk-amd64`), Maven 3.9.16 (also embedded in
  the wrapper). These defaults are local-development choices, not
  production recommendations: change them through environment variables
  and keep real passwords in a secret manager.
- **No container test story** — Docker is not available here, so
  Testcontainers-based tests were not used. The tests therefore need a
  real (disposable) PostgreSQL database, as documented in README §1/§5.
- **Rate limiter is single-node/in-memory** — fine for a single app
  instance; a multi-node deployment needs a shared store (e.g. Redis).
  Restarting the app also resets counters.
- **No browser-tool verification** — see §2 "Not verified".
- **No production hardening checklist yet**: TLS termination/proxy
  configuration, session `secure` cookie behind HTTPS
  (`SESSION_COOKIE_SECURE=true` exists), log aggregation, and DB backup
  procedures are operator tasks that are not exercised in this repo.
- **Bootstrap admin password** — the first admin password lives only in
  the launching environment. Anyone deploying must change it after first
  sign-in (the app logs a reminder).

## 7. Suggested next steps (Phase 2+)

1. Admin UI for enrollment history browsing/CSV export; batch rotation
   reports (active/completed/withdrawn over time).
2. Attendance and fee collection modules (schema + screens + tests in
   the same style).
3. A Docker-based "one command" developer environment
   (`docker-compose.yml` + Testcontainers for tests once Docker exists).
4. Optional multi-node rate limiting via Redis (or DB-backed counters).
5. Automated nightly full-app smoke in CI, plus an image of the login
   pages rendered by a real browser to close the visual-verification
   gap above.
