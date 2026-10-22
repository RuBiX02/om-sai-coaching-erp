# OM SAI COACHING ERP — CLIENT READINESS AUDIT

**Date:** 21 October 2026
**Scope:** Full repository audit of the existing Om Sai Coaching ERP, followed by one narrowly scoped repair (class/subject lifecycle in batches).
**Stack (unchanged):** Java 25, Spring Boot 4, Thymeleaf 3, Spring Security 7, PostgreSQL 18, Spring Data JPA / Hibernate 7, Flyway 12, Maven, JUnit 5, Playwright for Java, Bootstrap 5.
**No new project was created. Nothing was committed or pushed. No database was reset, dropped, reseeded or deleted.**

---

## 1. ACTUAL REPOSITORY STATE

### 1.1 Git

| Item | Value |
|---|---|
| Branch | `master` |
| HEAD at audit start | `b27b1ca` — *feat: add attendance CSV exports and printable sheets* |
| Commits in history | 4 (`b27b1ca`, `608dff0`, `6c91c23`, `9993d06`) |
| Stashes | none |
| Staged changes | none |

**The working tree was NOT clean at audit start.** A large body of uncommitted work existed — the timetable module, the examination module, installment plans, the student portal, the teacher dashboard, the production profile, migrations V3–V6, backup/restore scripts and roughly 2 000 lines of template/service changes.

**All of it was preserved.** A byte-level baseline was captured before any edit:

```
/tmp/opencode/baseline-unstaged.patch   2853 lines   (git diff of the working tree)
/tmp/opencode/baseline-untracked.txt      68 lines   (git ls-files --others)
```

Every one of those 29 modified files and 68 untracked files is still present and was not reset, cleaned, overwritten or discarded. New work was **additive**: new files were created, and existing files were edited in place with minimal, targeted diffs.

### 1.2 Files touched by this audit

| File | Change |
|---|---|
| `src/main/resources/db/migration/V7__batch_subject_lifecycle.sql` | **new** — adds `batch_subjects.active` |
| `src/main/java/com/omsai/coaching/batch/BatchSubject.java` | added `active` field + accessors |
| `src/main/java/com/omsai/coaching/batch/BatchSubjectRepository.java` | active-aware pickers, `existsActiveByBatchIdAndCourseSubjectId` |
| `src/main/java/com/omsai/coaching/batch/BatchService.java` | lifecycle rules, dependency guard, `SubjectUsage` |
| `src/main/java/com/omsai/coaching/batch/BatchController.java` | `POST /batches/subjects/{id}/status`, per-row usage map |
| `src/main/java/com/omsai/coaching/attendance/ClassSessionRepository.java` | (unchanged by this audit) |
| `src/main/java/com/omsai/coaching/timetable/TimetableEntryRepository.java` | `countByBatchSubjectId` |
| `src/main/java/com/omsai/coaching/examination/ExaminationRepository.java` | `countByBatchSubjectId` |
| `src/main/resources/templates/batches/detail.html` | lifecycle actions, explanations, disambiguated labels |
| `src/main/resources/static/css/app.css` | `.w-min-9rem` helper (CSP-safe width) |
| `src/test/java/com/omsai/coaching/batch/BatchSubjectLifecycleTest.java` | **new** — 16 service tests |
| `src/test/java/com/omsai/coaching/web/BatchSubjectAccessWebTest.java` | **new** — 9 HTTP/authorization tests |
| `src/test/java/com/omsai/coaching/browser/BatchSubjectLifecycleBrowserTest.java` | **new** — 3 real-Chromium tests |

### 1.3 Database configuration (inspected without printing secrets)

Three separate PostgreSQL databases exist on `127.0.0.1:5433`:

| Database | Used by | Flyway version at start | Purpose |
|---|---|---|---|
| `omsai` | default profile | 6 | development / demo data |
| `omsai_test` | `application-test.yml` (`@ActiveProfiles("test")`) | 6 | disposable unit-test database |
| `omsai_browser` | `application-browser.yml` (`@ActiveProfiles("browser")`) | 6 | disposable real-browser database |

The browser suite additionally refuses to start unless the **live connection** reports a database name starting with `omsai_browser`, so an environment override cannot redirect it at `omsai` or `omsai_test`. Credentials were read from configuration only and are **not** reproduced here beyond the already-committed local development defaults.

---

## 2. TEST BASELINE AND RESULTS

All numbers below were **measured in this session**, not copied from documentation.

### 2.1 Baseline before any change

| Suite | Command | Result |
|---|---|---|
| Unit + web | `./mvnw -o test` | **252 tests, 0 failures, 0 errors** |
| Unit + web + browser | `./mvnw -o test -Pbrowser-tests` | **276 tests, 0 failures, 0 errors** |

The historical figures quoted in `README.md` / `PROJECT_STATUS.md` (252 and 276) are therefore **accurate**.

### 2.2 After the repair

| Suite | Result | Delta |
|---|---|---|
| Unit + web | **277 tests, 0 failures** | +25 |
| Unit + web + browser | **304 tests, 0 failures** | +28 |

New tests: 16 service + 9 web/authorization + 3 browser = 28.

### 2.3 End-to-end smoke suite (real HTTP against the running application)

`zsh scripts/e2e-smoke.sh` was re-run against the restarted development
application after the repair:

```
208 checks, 0 failed.
All end-to-end smoke checks passed.
```

The smoke script exercises the real HTTP surface, including CSRF, the
login rate limiter, the teacher/student portals and the batch-subject
teacher assignment — all still green.

### 2.4 What was NOT done

* No failing test was suppressed, skipped or deleted.
* No test was weakened — the new browser test was made **stricter** twice during this session (see §10.3).
* No database was reset, dropped or reseeded. The dev database received exactly one change: the additive `V7` migration.

---

## 3. SOURCES OF MOCK OR DEMONSTRATION INFORMATION

The whole repository was searched for seed data, fabricated operational figures, hard-coded defaults and demo credentials.

### 3.1 Production startup creates no demonstration data — CONFIRMED

* **No `data.sql`, no `import.sql`, no seed script.** The only `.sql` files under `src/main/resources` are the six Flyway migrations.
* **Every migration is schema-only.** Verified by inspecting all of `V1__init.sql` … `V6__student_portal.sql`: they contain `CREATE TABLE`, `ALTER TABLE`, `CREATE INDEX` and constraint definitions. The only data statement is a single `UPDATE enrollments …` in `V2` that back-fills a column on rows that already existed — it inserts nothing.
* **Exactly one `CommandLineRunner` exists** — `AdminBootstrapRunner`. It:
  * is **off by default** (`APP_BOOTSTRAP_ADMIN=false`);
  * requires name, email **and** password to be supplied from the environment;
  * refuses a password shorter than 12 characters;
  * is **idempotent** — it does nothing if any administrator already exists;
  * stores only a BCrypt hash and never logs the password.

  This satisfies "a bootstrap administrator is allowed if provisioned securely through the documented process".

### 3.2 No fabricated operational figures — CONFIRMED

Every number on the dashboards and detail pages is produced by a live database query (`DashboardService`, `InstallmentService`, `TeacherDashboardService`, `StudentPortalService`). The `₹ 12 000.00`-style strings that appear in the Thymeleaf sources are **prototype/fallback text inside `th:text` elements** and are always replaced at render time — they are never displayed. A scan of all templates for static numeric text nodes outside `th:text`/`th:value` returned exactly one hit, a UI *label* ("Installments due in 7 days"), not data.

### 3.3 Demo credentials — production is clean, development is not

| Location | Finding | Scope | Risk of change |
|---|---|---|---|
| `src/main/resources/application.yml` | Inline development-only DB-password default, plus the local DB-username default **(literal redacted for upload)** | development | Low — standard local default~~, matches `.env.example`~~. *Resolved 2026-10: inline defaults removed from every configuration file; credentials are supplied through environment variables only.* |
| `src/main/resources/application-prod.yml` | **No defaults at all** — `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` are mandatory; `APP_BOOTSTRAP_ADMIN` defaults to `false`; `APP_ADMIN_PASSWORD` has no default; BCrypt cost 12 | production | **None — this is correct** |
| `scripts/e2e-smoke.sh:6,21` | Admin sign-in name + password were committed in the repository **(values redacted for upload)** | development/test | Low technically, see §7.3 — the live dev admin still used this password. *Resolved 2026-10: the script now reads `OMSAI_ADMIN_EMAIL` / `OMSAI_ADMIN_PASS` / `OMSAI_DB_PASSWORD` from the environment and stores no credentials.* |
| `src/test/resources/application-browser.yml:51` | Browser-test bootstrap admin password **(literal redacted for upload; test only)** | **test only** (disposable `omsai_browser`) | None |
| `src/test/java/.../BrowserTestBase.java:79` | Browser-test bootstrap admin password **(literal redacted for upload; test only)** | **test only** | None |
| `scripts/e2e-smoke.sh:355,876` | E2E fixture passwords the run creates itself **(literals redacted for upload; test only)** | **test only** | None |
| `src/main/java/.../AdminBootstrapRunner.java` | No default password — must come from the environment | production | None |

**Conclusion:** there is **no fabricated production data and no hard-coded production password.** The only weakness is that the *development* administrator still uses the password documented in the smoke-test script.

### 3.4 Records in the development database

The `omsai` development database holds a mixture of **three** kinds of record. Only the first is unambiguously test data.

**(a) E2E records — unambiguously test data, written by `scripts/e2e-smoke.sh`:**

| Table | Count | Identifying pattern |
|---|---|---|
| `user_accounts` | 49 | full name `E2E Teacher Account <timestamp>` / `E2E Student Portal`, email `e2e.<role>.<timestamp>@omsai.local` |
| `teachers` | 71 | full name `E2E Teacher Account <timestamp>` |
| `students` | 76 | full name `E2E Student …` |
| plus their batches, subjects, enrollments, sessions, attendance, fee agreements and payments | | all reachable from the above |

These carry the literal `E2E` prefix that the smoke script writes, they are timestamped, and their password is the one hard-coded in that script.

**(b) Ordinary demonstration records — real-looking client data, PRESERVED:**

| Record | Detail |
|---|---|
| Course **"Class 10 - CBSE"** | the only realistic course |
| Batches **"Morning Batch A"** and **"Morning Batch B"** (2026-27) | the two realistic batches |
| Student **Aarav Sharma** (`OMS-2026-00001`, admitted 2026-04-01) | ACTIVE enrollment in *Class 10 - CBSE / Morning Batch A* since 2026-04-10 |
| Student **Dia Patel** (`OMS-2026-00002`, admitted 2026-04-05) | ACTIVE enrollment in *Class 10 - CBSE / Morning Batch B* since 2026-06-05 |
| Subjects **"Mathematics"** and **"Physics"** | the realistic subjects |

These have realistic names, realistic admission dates spread over months and coherent course/batch structure — **they are not test data and were not touched.** No record was classified as test data *merely because its name looked unusual*.

**(c) Unidentified debug artifacts — flagged, NOT deleted:**

| Record | Why it is not being classified |
|---|---|
| 7 courses named `DBg Course …`, `FF Course …`, `FG Course A/C/D/E/F/G …` | the pattern suggests manual debugging, but they were not written by the committed smoke script and their provenance cannot be established from the repository |
| 1 subject named **"Debug Subject"** | same |

**(d) The administrator account** `admin@omsai.local` — the only non-E2E account.

**All of (a), (b), (c) and (d) were left exactly as they were.** The only change the development database received in this session is the additive `V7` migration plus the E2E-prefixed rows that re-running the project's own smoke script creates by design.

A cleanup of (a) and (c) is **proposed but not executed** — see §13. It must be reviewed separately, must show exactly which records it would affect, and must never run automatically against an unknown database.

---

## 4. CONFIRMATION OF WHICH SOURCES ARE TEST-ONLY

| Source | Profile | Database | Proof |
|---|---|---|---|
| `src/test/resources/application-test.yml` | `test` | `omsai_test` | `@ActiveProfiles("test")` on every unit/web test |
| `src/test/resources/application-browser.yml` | `browser` | `omsai_browser` | `@ActiveProfiles("browser")` + live-connection guard in `BrowserTestBase.requireDisposableDatabase()` |
| `src/test/java/.../support/Fixtures.java` | test only | — | Spring `@Component` under `src/test`, never packaged into the application |
| `BrowserTestBase.deleteOwnData()` | test only | `omsai_browser` | Deletes **only** rows the test itself created, tracked by id |
| `scripts/e2e-smoke.sh` | manual, against a running app | `omsai` by default | Prefixes everything it creates with `E2E` + timestamp |

**No fixture, seed or demo row can reach the production application.** `Fixtures` lives under `src/test/java` and is not on the application classpath. There is no `CommandLineRunner` that inserts demo rows.

---

## 5. MODULE / ACTION MATRIX

Legend: **✅ COMPLETE AND VERIFIED** · **🟡 IMPLEMENTED BUT NOT VERIFIED** · **🔴 MISSING ACTION** · **💥 ACTION EXISTS BUT FAILS** · **🔒 BLOCKED BY BUSINESS RULE** · **❓ REQUIRES CLIENT DECISION** · **— NOT APPLICABLE**

Verification note: "VERIFIED" means the behaviour was traced through controller → service → database constraint *and* exercised by an automated test in this session.

### A. Courses and subjects

| Action | Course | Subject (standalone) | Subject inside a course (`course_subjects`) |
|---|---|---|---|
| List | ✅ | ✅ | ✅ (on course page) |
| Detail | ✅ | ✅ | ✅ |
| Create | ✅ | ✅ | ✅ |
| Edit | ✅ (name, category, duration) | ✅ | 🔴 **name not editable** |
| Deactivate / reactivate | ✅ `POST /courses/{id}/status` | 🔴 none | 🔴 none |
| Remove | — (deactivate is the correct action) | 🔴 **MISSING** | 🔴 **MISSING** |
| Duplicate handling | ✅ | ✅ | ✅ |
| Referenced-data handling | ✅ blocks removal while taught | ✅ blocked by FK RESTRICT → generic error | ✅ `CourseService.removeSubject` guards with a clear message |
| Explanation when blocked | ✅ | ❌ generic 409 | ✅ |

### B. Batches and batch-subject assignments

| Action | Batch | BatchSubject assignment ("the class") |
|---|---|---|
| List | ✅ | ✅ (on batch page) |
| Detail | ✅ | ✅ (row on batch page) |
| Create | ✅ | ✅ |
| Edit | ✅ (name, year, dates; course fixed) | 🟡 teacher only |
| Activate / deactivate | ✅ `POST /batches/{id}/status` | 🔴 **MISSING — repaired in §10** |
| Assign / reassign teacher | ✅ `POST /batches/subjects/{id}/teacher` | ✅ |
| Remove assignment | — | 💥 **existed but failed with a misleading DB error — repaired in §10** |
| Preserve history | ✅ enrollments preserved | ✅ classes/attendance/exams preserved |

### C. Student management

| Action | Status |
|---|---|
| Register | ✅ |
| View profile | ✅ |
| Edit permitted information | ✅ (phone, email, guardian) |
| Activate / deactivate | ✅ `POST /students/{id}/status` |
| Enroll in a batch | ✅ `POST /students/{id}/enroll` |
| Transfer between batches | ✅ `POST /students/{id}/transfer` (history kept) |
| Complete / withdraw enrollment | ✅ `POST /students/enrollments/{id}/status` |
| Correct errors | ✅ via edit + transfer + withdrawal |

### D. Teacher management

| Action | Status |
|---|---|
| Create profile | ✅ |
| Edit profile | ✅ |
| Activate / deactivate | ✅ `POST /teachers/{id}/status` |
| Link login account | ✅ (on create and on edit) |
| Unlink login account | ✅ (edit form with empty selection) |
| Assign / reassign subjects | ✅ via batch-subject assignment |
| Remove obsolete assignment | ✅ teacher can be cleared from an assignment; the assignment itself is now deactivatable (§10) |

### E. Class sessions and attendance

| Action | ClassSession | Attendance |
|---|---|---|
| Create | ✅ | ✅ |
| View | ✅ | ✅ (session page, roster, print sheet) |
| Edit | ✅ **OPEN only**, clear message otherwise | ✅ corrections re-save the roster |
| Cancel | ✅ **OPEN only**; blocked when attendance exists, with the exact wording *"This session already has attendance records, so it cannot be cancelled. Complete it instead."* | — |
| Complete | ✅ | ✅ |
| Reports | ✅ | ✅ student / batch / subject / date + CSV export |
| Print | ✅ | ✅ printable sheet, verified under Chromium print emulation |
| Teacher ownership | ✅ enforced in `SessionAuthority` (service layer, not just hidden buttons) | ✅ |

### F. Timetable

| Action | Status |
|---|---|
| Create recurring schedule | ✅ |
| View | ✅ weekly view + detail |
| Edit | ✅ |
| Deactivate | ✅ `POST /timetable/{id}/deactivate` — *"The classes it generated are kept."* |
| Holidays | ✅ `calendar_exceptions` |
| Cancel an individual occurrence | ✅ via a calendar exception of kind *cancelled* |
| Reschedule an occurrence | ⚠️ via editing the schedule — no single-occurrence reschedule |
| Conflict detection | ✅ teacher / batch / classroom, overlapping times and effective periods |
| Generate class sessions | ✅ `POST /timetable/generate` |
| Avoid duplicates | ✅ idempotent per assignment + date |

### G. Examinations and results

| Action | Status |
|---|---|
| Create | ✅ |
| Edit | ✅ **DRAFT only** |
| Cancel | ✅ `POST /examinations/{id}/cancel` |
| Enter marks | ✅ |
| Correct marks | ✅ before publication |
| Publish | ✅ administrator only |
| Correct published results | 🟡 results are re-saved with `entered_by` / `updated_at`; there is no separate "amendment" workflow — **see §12, C6** |
| Preserve historical results | ✅ never deleted |

### H. Fees and installments

| Action | Status |
|---|---|
| Create fee agreement | ✅ |
| Edit permitted fields | ✅ |
| Create installment schedule | ✅ (must sum exactly to the net fee) |
| View schedule | ✅ overview / upcoming / overdue |
| Upcoming & overdue | ✅ |
| Preserve financial history | ✅ |
| Correct a mistake | ✅ **REPAIRED — see §15.** A plan may be replaced while it carries no payment-allocation history; once money is booked against it, the schedule is immutable and the reason is explained. |

### I. Payments and receipts

| Action | Status |
|---|---|
| Record payment | ✅ |
| View payment | ✅ |
| View / print receipt | ✅ (print verified via Chromium + PDFBox) |
| Void with audit reason | ✅ `status`, `voided_by`, `voided_at`, `void_reason` |
| Search / filter | ✅ |
| Prevent deletion / modification | ✅ **no delete or edit endpoint exists — correct** |
| Reconcile allocations with installments | ✅ allocation + reconciliation check inside the payment transaction |

### J. User accounts and portals

| Action | Admin | Teacher | Student |
|---|---|---|---|
| Create account | ✅ | — | — |
| Link to student / teacher | ✅ with duplicate detection | — | — |
| Unlink | ✅ (`UserService.unlinkStudent`) | ✅ | — |
| Deactivate | ✅ (last admin protected) | ✅ | ✅ |
| Reset / change password | ✅ admin reset | ✅ own password | ✅ own password |
| Role restrictions | ✅ | ✅ read-only + own classes only | ✅ own records only |
| Deactivated user / stale session | ✅ | ✅ | ✅ |

---

## 6. MISSING OR BROKEN WORKFLOW INVENTORY

| # | Severity | Module | Problem |
|---|---|---|---|
| **1** | **P1** | Batch / batch-subject | Removing a subject assignment that has classes fails with a misleading database error; there is no alternative action. **REPAIRED (§10).** |
| 2 | P1 | Fees / installments | An installment plan cannot be edited or replaced once created. |
| 3 | P2 | Subjects | A standalone subject has no delete and no deactivate action. |
| 4 | P2 | Course subjects | A subject's name inside a course cannot be edited. |
| 5 | P2 | Timetable | No single-occurrence reschedule (only whole-schedule edits). |
| 6 | P2 | Examinations | Published results have no distinct "amendment" workflow. |
| 7 | P2 | Several | Two different "Deactivate" buttons with different meanings shared one page (batch vs. subject). **REPAIRED (§10).** |

---

## 7. SECURITY AND DATA-INTEGRITY FINDINGS

### 7.1 P0 — Critical data or security risk

**None found.** Specifically verified:

* Every mutating endpoint sits behind `@PreAuthorize` and the service layer repeats the ownership check (`SessionAuthority`, `ExaminationService.requireCanManage`) — hiding a button is never the only control.
* The teacher and student portals are scoped to the signed-in user's own records; the teacher portal cannot open another teacher's class, and the student portal resolves its student from the authenticated account, never from a request parameter.
* Payments are append-only with an audited void; there is no delete or edit endpoint, and `payments`/`fee_accounts` carry `ON DELETE RESTRICT` foreign keys.
* All foreign keys that protect history are `ON DELETE RESTRICT` (verified against the live schema, §8.2).
* Login is rate-limited (5 attempts / 15 min per username + IP) and the lockout is per username **and** IP, so one user cannot lock out another.
* BCrypt cost 12 in production, 4 in test profiles.
* Production requires every secret from the environment and refuses to start without it.
* The CSP (`default-src 'self'; style-src 'self'; …`) blocked a real inline-style attempt during this session — the browser suite caught it.

### 7.2 P1 — Incorrect balances / accidental loss of history

* **Payment voiding** is the only way to reverse a transaction and it records who, when and why. It is never replaced by deletion. ✔
* **Installment allocation** happens inside the payment transaction; a payment that cannot be fully allocated rolls the whole payment back. ✔
* **Fee agreement edits** and **installment creation** are transactional; a schedule that does not sum to the net fee is rejected with the exact difference shown. ✔
* **Class sessions, attendance, timetable entries and examinations** all reference the batch-subject assignment with `ON DELETE RESTRICT`, so history cannot be destroyed by accident. ✔ — *the repair in §10 makes this explicit instead of leaving it to a raw database error.*

### 7.3 P2/P3 — Development-environment weaknesses (not production)

* The **live development administrator still uses the password that used to be documented in the smoke-test script** (value redacted for upload), and `README.md` itself says *"Please sign in and change this password soon"*. It is stored only as a BCrypt hash, but it is published in the repository **history** — so this credential must still be treated as exposed (rotate it) and any future history sanitization must cover old commits.
* The development database holds 45 leftover E2E accounts and a set of
  debug artifacts alongside two genuine demonstration students (§3.4).

**Neither is a production defect** — the production profile has no built-in password — but both should be cleaned up before the system is handed over.

---

## 8. ROOT CAUSE OF THE CLASS DELETION / CANCELLATION ISSUE

> *"A class can be created, but the appropriate removal or cancellation workflow is unclear or missing."*

### 8.1 Which entity does "class" refer to?

Four candidates were audited **separately**, as required:

| Candidate | Lifecycle before this audit | Verdict |
|---|---|---|
| **ClassSession** (one-off class) | `OPEN → COMPLETED` / `OPEN → CANCELLED`. Edit only while OPEN. Cancel refused when attendance exists, with a precise message. Detail page carries all three actions with confirmation dialogues and success/error messages. | ✅ **Already complete.** Not the culprit. |
| **TimetableEntry** (recurring schedule) | Create / edit / **deactivate**, with *"Deactivate this recurring class? The classes already generated and their attendance are kept."* | ✅ **Already complete.** Not the culprit. |
| **Batch** (the group of students) | Create / edit / **activate & deactivate**, with *"Batch deactivated. Enrollments and history are preserved."* | ✅ **Already complete.** Not the culprit. |
| **BatchSubject assignment** (one subject of one course, taught in one batch, by one teacher) | **No lifecycle action at all.** A single raw `deleteById()`. | ❌ **This is the culprit.** |

The **batch-subject assignment** is the entity that actually *is* a class: it is what a class session, a timetable entry and an examination all hang from. It was the only one of the four without a cancel/deactivate/archive action.

### 8.2 Root cause

`BatchService.removeSubject()` (before the repair):

```java
@Transactional
public void removeSubject(Long batchSubjectId) {
    batchSubjectRepository.findById(batchSubjectId)
            .orElseThrow(() -> new BusinessRuleException("Batch subject assignment not found."));
    batchSubjectRepository.deleteById(batchSubjectId);   // <-- unguarded hard delete
}
```

The three foreign keys that protect the history are `ON DELETE RESTRICT` (verified against the live schema):

```
class_sessions     → batch_subjects   ON DELETE RESTRICT
timetable_entries  → batch_subjects   ON DELETE RESTRICT
examinations       → batch_subjects   ON DELETE RESTRICT
```

So as soon as the subject had been taught even once, the delete raised a `DataIntegrityViolationException`, which fell through to the generic handler in `GlobalExceptionHandler`:

```
HTTP 409 — "Something went wrong
           That change conflicts with existing data - a record with the same details may already exist.
           You can try again. If the problem keeps happening, contact the system administrator."
```

**Reproduced against the running application before the repair** (batch-subject 12, which has two class sessions):

```
POST /batches/subjects/12/remove?batchId=33  →  HTTP 409
```

Three defects in one:

1. **The message is factually wrong.** It says *"a record with the same details may already exist"* — a duplicate-record problem. The real cause is a *dependency*: classes exist that point at this assignment.
2. **The user is left with no path forward.** The message says *"contact the system administrator"*, but an administrator has no way to remove the subject either. The confirm dialogue on the button even claimed *"Enrollment history of students is not affected"*, implying the removal would succeed.
3. **The UI misled.** The page offered a delete button for an operation the backend always refuses.

### 8.3 Why it had not been noticed

The **sibling method** `CourseService.removeSubject()` — removing a subject from a *course* — *does* have exactly the right guard:

```java
if (courseSubjectRepository.countAssignments(courseSubjectId) > 0) {
    throw new BusinessRuleException(
        "This subject is taught in one or more batches of this course, so it cannot be removed "
                + "without losing teaching history. Remove the batch assignment first.");
}
```

The intended pattern existed one level up and was simply never applied to the batch-subject level. There was also **no test** covering removal of a used assignment.

### 8.4 Why "deactivate" is the correct operation (not "delete")

`Course`, `Batch` and `TimetableEntry` all already implement deactivation. Deactivating an assignment:

* takes it out of every picker, so no **new** class session, timetable entry or examination can be created for it;
* leaves every existing class session, attendance mark and result exactly where it is;
* is reversible — the subject can be taught again with its teacher and history intact.

This matches the established business model ("the application intentionally preserves historical records") and does not weaken a single foreign key.

---

## 9. LIFECYCLE POLICY APPLIED

| Entity | Correct operation | Rationale |
|---|---|---|
| ClassSession | **Cancel** (not delete) | One occurrence of a class. Already implemented. |
| TimetableEntry | **Deactivate** (not delete) | A recurring schedule. Already implemented. |
| Batch | **Deactivate** (not delete) | Enrollments and history must survive. Already implemented. |
| BatchSubject | **Deactivate** (not delete) once used; **hard delete** only while never used | **Newly implemented.** Mirrors `CourseService.removeSubject`. |
| CourseSubject | Guarded hard delete | Cannot be removed while taught in a batch. Already implemented. |
| Payment | **Void** (never delete) | Audited financial correction. Already implemented. |
| Enrollment | **Withdraw / complete** (never delete) | Historical lifecycle transition. Already implemented. |
| Examination | **Cancel** (never delete) | Preserves the result history. Already implemented. |

---

## 10. THE FIRST REPAIR — BATCH-SUBJECT LIFECYCLE

### 10.1 What changed

**A. New migration `V7__batch_subject_lifecycle.sql`**

```sql
ALTER TABLE batch_subjects
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;

CREATE INDEX ix_batch_subjects_active ON batch_subjects (active);
```

Additive and safe: existing rows stay `true`, so no existing record changes meaning. Applied automatically by Flyway to `omsai`, `omsai_test` and `omsai_browser` (logged as *"Successfully applied 1 migration to schema 'public', now at version v7"*).

**B. `BatchSubject`** — new `active` column with `isActive()` / `setActive()`, defaulting to `true`.

**C. `BatchSubjectRepository`** — the pickers now only offer **active** assignments:

| Query | Change |
|---|---|
| `findForActiveBatches()` | `+ and bs.active = true` — the "schedule a class" picker |
| `findAllWithContext()` | `+ and bs.active = true` — the timetable and examination pickers |
| `findForUser()` | `+ and bs.active = true` — a teacher's own assignments |
| `findForTeacher()` | `+ and bs.active = true` — a teacher's assignments |
| `findForBatch()` | **unchanged filter** — the batch page still shows deactivated rows (active first) so they can be reactivated |
| `findByBatchIdAndCourseSubjectId()` | unchanged — historical reports still resolve inactive assignments |
| `existsActiveByBatchIdAndCourseSubjectId()` | **new** |

**D. `BatchService`** — three new/changed behaviours:

* `removeSubject(id)` — now checks the dependencies first. If anything depends on the assignment it is **refused with an actionable message** instead of failing with a database error:

  > *"This subject cannot be removed from the batch because 2 class sessions depend on it. That teaching history must be preserved. Deactivate the subject instead to stop offering it for new classes."*

  If nothing depends on it, the assignment is deleted as before.

* `setSubjectActive(id, active)` — **the new lifecycle action.** Deactivating keeps everything and hides the assignment from the pickers; reactivating brings it back. Reactivating is refused when the batch or its course is inactive.

* `addSubject(...)` — when the subject was taught in this batch before and only deactivated, the existing assignment is **reactivated** instead of colliding with the unique constraint (`ux_batch_subjects_batch_subject`).

* `usageOf(id)` — returns a `SubjectUsage` record with the three dependency counts, so the page can offer the right action.

**E. `BatchController`**

* `POST /batches/subjects/{batchSubjectId}/status?active=true|false` — the new lifecycle endpoint.
* `POST /batches/subjects/{batchSubjectId}/remove` — now returns a **redirect with a readable error message** instead of a 409 error page.
* The batch detail page receives a `usage` map so each row can show its own state.

**F. `batches/detail.html`**

| Situation | Action shown | Explanation |
|---|---|---|
| Never used | **Remove subject** (delete) | *"It has never been used for a class, so no history is lost."* |
| Has classes / schedule / exam, active | **Deactivate subject** | *"Cannot be removed: 2 class sessions depend on it."* |
| Deactivated | **Activate subject** + "Deactivated" badge | — |
| Any row | Card-footer note explaining that a subject with history cannot be deleted and must be deactivated instead | |

The batch's own button was given the tooltip *"Deactivate batch"* and the row button the label *"Deactivate subject"*, so two different "Deactivate" actions on one page can no longer be confused.

**G. `app.css`** — added `.w-min-9rem` (a width helper) because the CSP forbids inline `style` attributes. The existing file already documents this convention.

### 10.2 What was explicitly NOT done

* No `batch_subjects` row was deleted that has classes, attendance, a timetable entry or an examination.
* No foreign key was dropped, relaxed or re-pointed.
* No class session was deleted.
* No payment, enrollment or examination was touched.
* The "delete everything" button was **not** generalised onto other entities.
* No new business module was added; no broad UI rewrite was performed.

### 10.3 Two bugs the repair itself introduced — and how they were caught

Both were found during validation, and the test suite was tightened rather than the assertion relaxed:

1. **Both "Deactivate" and "Activate" rendered at the same time.** The first version used `th:unless="${usage[bs.id].removable and bs.active}"`, which is true whenever the subject is *not removable*. Caught by a **live HTTP check against the running application** (the unit and browser tests had passed). Fixed to `th:if` with the correct condition, and the browser test now asserts that exactly one of the two is present.
2. **An inline `style="min-width: 9rem"` violated the CSP** (`style-src 'self'`). Caught by the existing `assertNoClientOrServerErrors()` check in the browser suite. Replaced with a CSS class, following the convention already documented in `app.css`.

---

## 11. TESTS EXECUTED FOR THE REPAIR

### 11.1 Service layer — `BatchSubjectLifecycleTest` (16 tests)

```
anAssignmentThatWasNeverUsedCanBeRemoved            PASS
anAssignmentWithClassSessionsCannotBeRemoved         PASS
anAssignmentWithATimetableEntryCannotBeRemoved       PASS
anAssignmentWithAnExaminationCannotBeRemoved          PASS
aRefusedRemovalLeavesTheAssignmentAndItsClassesUntouched  PASS
usageCountsEveryKindOfDependency                      PASS
anUnusedAssignmentReportsNoDependencies               PASS
deactivatingHidesTheAssignmentFromThePickers          PASS
deactivatingKeepsTheAssignmentVisibleOnTheBatchPage   PASS
deactivatingKeepsEveryExistingClass                   PASS
reactivatingPutsTheAssignmentBackIntoThePickers       PASS
deactivatingTwiceIsRefusedWithAnExplanation           PASS
activatingAnActiveAssignmentIsRefused                 PASS
aSubjectOfAnInactiveBatchCannotBeReactivated          PASS
addingADeactivatedSubjectReactivatesItInsteadOfDuplicatingIt  PASS
aDeactivatedSubjectCanBeAddedAgainFromThePicker       PASS
```

### 11.2 HTTP / authorization layer — `BatchSubjectAccessWebTest` (9 tests)

```
adminCanDeactivateAnAssignment                        PASS
adminCanReactivateAnAssignment                        PASS
adminCanRemoveAnAssignmentThatWasNeverUsed            PASS
removingAnAssignmentThatHasClassesIsRefusedWithAnExplanation  PASS
aRefusedRemovalDoesNotDeleteAnything                  PASS
deactivatingTwiceIsRefusedWithAnExplanation           PASS
teacherCannotReachTheBatchPages                       PASS   (403 on GET + POST)
anonymousUsersAreRedirectedToLogin                    PASS
theBatchPageStillRendersWithALifecycleAction          PASS
```

### 11.3 Real browser — `BatchSubjectLifecycleBrowserTest` (3 tests, real Chromium)

```
aSubjectWithClassesIsDeactivatedInsteadOfRemoved      PASS
deactivatingASubjectKeepsItsClassesAndAttendance      PASS
aSubjectThatWasNeverUsedCanStillBeRemoved             PASS
```

All three drive a real headless Chromium against the real application on a real port, with the suite's built-in checks that **no console error, no failed request and no 4xx/5xx response** occurred.

### 11.4 Full suite

```
./mvnw -o test                 → 277 tests, 0 failures, 0 errors
./mvnw -o test -Pbrowser-tests → 304 tests, 0 failures, 0 errors
```

### 11.5 Live verification against the running application

The development application was restarted so that migration V7 was applied to the `omsai` database (logged: *"Migrating schema 'public' to version '7 - batch subject lifecycle'"*), and the repaired page was fetched over real HTTP:

```
button titles on the page:
   - Deactivate batch          ← the batch's own action, now unambiguous
   - Assign teacher
   - Deactivate subject        ← the subject's action
   (no "Remove subject" button, because the subject has classes)

explanation: "Cannot be removed: 2 class sessions depend on it."
```

### 11.6 Visual evidence

`target/browser-artifacts/batch-subject-lifecycle.png` — a full-page screenshot of the repaired batch page, taken by the real-Chromium test. It shows the green success banner *"Subject deactivated. Its classes, attendance and results are kept, and no new class can be scheduled for it."*, the "Deactivated" badge, the **Activate subject** button and the explanatory card footer.

> Note: the interactive desktop browser tool was **not connected** in this session (`browser.disconnected`). All visual evidence above therefore comes from the Playwright/Chromium suite, which is genuine browser automation against the running application.

### 11.7 Data-integrity check

After the whole repair:

* The refused removal was verified to roll back cleanly — `batch_subjects` row 12 still present, both of its class sessions still present.
* The repair never writes to the development database except the additive migration. The browser suite operates exclusively on the disposable `omsai_browser` database and deletes exactly the rows it created.

---

## 12. REMEDIATION BACKLOG

### P0 — Critical data or security risk

**None.** No P0 findings remain.

### P1 — Core client workflow is broken

| # | Module | Observed problem | Root cause | Proposed change | Regression tests needed | Depends on |
|---|---|---|---|---|---|---|
| **P1-1** | Batch / batch-subject | Removing a subject assignment that has classes failed with a misleading database error, and no alternative action existed | Unguarded `deleteById()` in `BatchService.removeSubject` | **DONE — see §10** | 16 service + 9 web + 3 browser tests | — |
| **P1-2** | Fees / installments | ~~An installment plan can be created but never edited or replaced~~ **REPAIRED — see §15** | `InstallmentService.createPlan` was the only entry point | ✅ Done: a plan may be replaced while it carries no payment-allocation history; once money is booked against it the schedule is immutable and the reason is explained | ✅ 24 service + 13 web + 3 browser + 1 concurrency tests |
| **P1-3** | Subjects | ~~A standalone subject cannot be deleted or deactivated, so a typo in a subject name can only be corrected by editing it, and an obsolete subject stays in the pickers forever~~ **REPAIRED — see §16** | No lifecycle method on `SubjectService` | ✅ Done: an unused subject may be deleted, a used subject is deactivated instead and can be reactivated; a deactivated subject leaves the pickers | ✅ 23 service + 10 web + 5 browser tests |

### P2 — Significant usability problem

| # | Module | Observed problem | Root cause | Proposed change | Regression tests needed |
|---|---|---|---|---|---|
| **P2-1** | Course subjects | The name of a subject inside a course cannot be edited | `CourseService` exposes no update for `course_subjects` | Allow renaming a course subject, with a duplicate-name check | Rename works; duplicate name refused; batch assignments keep their link |
| **P2-2** | Timetable | A single occurrence of a recurring class cannot be rescheduled | Only whole-schedule edits exist | Add "reschedule this occurrence" that creates a `calendar_exception` for the original date and a one-off class session for the new date | Original occurrence skipped, new one created exactly once |
| **P2-3** | Examinations | Published results can be re-saved, but there is no distinct, auditable "amendment" workflow | `recordResults` handles DRAFT and PUBLISHED alike | Add an explicit *amend published result* action that records who changed what and when, keeping the original value | Amending a published result keeps the original value in the history; only administrators may amend |
| **P2-4** | Dashboard / lists | Several list pages have no way to find an existing record quickly | Filtering is inconsistent across modules | Standardise the search/filter controls on every list page | — |
| **P2-5** | Payments | No reconciliation screen showing payment allocations against installments side by side | Allocations are only visible inside a fee agreement | Add a reconciliation view per fee account | — |
| **P2-6** | Fees (all pages) | **Found during the P1-2 repair (§15.10).** A *missing* fee account raises a `BusinessRuleException` that no global handler catches, so every fee page answers a guessed id with a 500 error page instead of a "not found" message | `BusinessRuleException` is not handled in `GlobalExceptionHandler`; only `AccessDeniedException` and `DataIntegrityViolationException` are | Handle `BusinessRuleException` globally with a friendly page (careful: several controllers rely on catching it themselves — the handler must only cover the ones that do not) **or** resolve the account with a nullable lookup in every fee controller, as the new installment-edit endpoints already do | A guessed fee-account id answers with a message, not a 500; the existing flows that catch the exception themselves keep working |

### P3 — Polish

| # | Finding |
|---|---|
| **P3-1** | Two different "Deactivate" buttons shared one page. **REPAIRED in §10** (batch vs. subject now labelled distinctly). |
| **P3-2** | CSV downloads produce an `ERROR` log with a stack trace when the client closes the connection early (`java.io.IOException: Broken pipe` → `IllegalStateException: getOutputStream() has already been called`). Harmless to the user — the download already succeeded — but it floods the log with false alarms and hides real errors. Found in the baseline run, before any change. Fix: handle `ClientAbortException` in the exception handler and do not try to render an error page on a committed response. |
| **P3-3** | The icon-only teacher-assignment button has no visible label; a `title` attribute was added to the subject buttons in §10 but the remaining icon-only buttons across the app could get the same treatment for accessibility. |
| **P3-4** | Currency and date formats are consistent (`MoneyFormat` + the shared `th:text` helpers), but no test asserts the rendered format of a receipt. |
| **P3-5** | The development administrator still uses the password published in `scripts/e2e-smoke.sh`. Change it before handover (production is unaffected — it has no built-in password). |

### Deliberately not raised as findings

* Icon choice, colour and spacing differences — cosmetic only, and explicitly excluded by the brief.
* The absence of a physical delete for payments, enrollments, class sessions and examinations — this is **correct by design**, not a gap.
* The absence of a "delete" for courses and batches — deactivation is the correct operation, and it exists.

---

## 13. RECOMMENDED ORDER FOR SUBSEQUENT REPAIRS

1. **P1-2 — Editable installment plans.** ~~A client that creates a wrong fee schedule today has no way out.~~ **DONE — §15.** A schedule may now be replaced while it carries no payment-allocation history; once money is booked against it, the schedule is immutable and the reason is explained.
2. ~~**P1-3 — Subject deactivation.**~~ **DONE — §16.** A subject can now be deactivated and reactivated; an unused one can also be deleted. The same "correct the mistake without losing the history" pattern as §10 and §15.
3. **P2-1 — Rename a course subject.** Tiny, and removes a frustrating dead end.
4. **P2-6 — A missing fee account must not produce a 500.** Found during the P1-2 repair; it affects every fee page and is a one-line-class fix once the interaction with the existing `BusinessRuleException` handling is settled.
5. **P2-3 — Auditable amendment of published results.** Needed before real examinations are held; results must never be silently rewritten.
6. **P2-2 — Reschedule a single timetable occurrence.** Valuable once a timetable is in daily use.
7. **P2-4 / P2-5 — Search and reconciliation screens.** Usability, once the data is correct.
8. **P3-2 — Stop the broken-pipe false alarms.** Cheap, and it protects the signal in the production log.
9. **P3-5 — Rotate the development administrator password** and hand over the credentials separately from the repository.

### Proposed development-database cleanup (NOT executed — requires separate approval)

The E2E records in `omsai` are clearly test data (§3.4 a). The debug
artifacts (c) are **not** classified, because their provenance cannot be
established. The ordinary demonstration records (b) must be preserved.

Before removing anything, the following must be done **manually and reviewed**:

1. Produce an exact list of the affected rows (`user_accounts` joined to
   `teachers` / `students`, plus their dependent batches, subjects,
   enrollments, sessions, attendance, fee agreements and payments).
2. Show the count of dependent rows for each.
3. Exclude every row that is **not** `E2E`-prefixed — in particular the
   two demonstration students and their enrollments.
4. Decide, row by row, whether the debug artifacts (c) are disposable.
5. Only then delete, from a backup taken with `scripts/backup-db.sh`
   (which already exists in the working tree).

**No cleanup script has been written and nothing has been deleted.** This
operation must never run automatically against an unknown database.

---

## 14. COMPLIANCE WITH THE BRIEF

| Requirement | Status |
|---|---|
| Preserve all existing uncommitted work | ✔ Baseline captured first; all 29 modified + 68 untracked files intact; new work is additive |
| Do not start a new project | ✔ Same Maven project, same stack, same package structure |
| Do not perform a broad UI rewrite | ✔ One detail page touched, one CSS helper added |
| Do not add new business modules | ✔ A lifecycle action on an existing entity, mirroring three existing entities |
| Do not delete real or unidentified database records | ✔ No record deleted; the only DB change is the additive `V7` migration |
| Do not change financial history | ✔ No payment, fee agreement, installment or receipt touched |
| Do not bypass authorization | ✔ New endpoint sits under the existing `@PreAuthorize("hasRole('ADMIN')")` on the controller; verified by tests |
| Do not claim visual testing without real browser evidence | ✔ Evidence is a real-Chromium screenshot; the unavailable desktop browser tool is stated explicitly |
| Do not commit or push | ✔ Nothing committed; HEAD is still `b27b1ca` |
| Do not deploy publicly | ✔ Local development application only |
| Do not reset / drop / reseed a database | ✔ No database was reset; Flyway applied one additive migration |
| Do not suppress failing tests | ✔ No test suppressed or weakened; the new browser test was made stricter twice |
| Record actual test results | ✔ 252 → 277 (unit) and 276 → 304 (with browser), all measured in this session |
| Produce `CLIENT_READINESS_AUDIT.md` | ✔ This document |
| Update `PROJECT_STATUS.md` | ✔ See the milestone entry added there |

---

## 15. THE SECOND REPAIR — P1-2: EDITING AN INSTALLMENT PLAN

> Implemented 2026-10-21, after §10 (P1-1). The scope is the
> installment-plan editing workflow only; no other audit finding was
> started.

### 15.1 Root cause of the missing editing workflow

`InstallmentService` had exactly one entry point for a schedule:

```java
public List<FeeInstallment> createPlan(Long feeAccountId, List<InstallmentInput> inputs, UserAccount admin) {
    FeeAccount account = feeAccountRepository.getForUpdate(feeAccountId)...;
    if (installmentRepository.existsByFeeAccountId(feeAccountId)) {
        throw new BusinessRuleException("This fee agreement already has an installment plan.");
    }
    ...
}
```

`createPlan` was **create-only by construction**: it refuses to run when a
plan already exists, and there was no second method that could take an
existing plan apart and write a new one. The controller mirrored the
same one-way street — `GET /fees/{id}/installments` redirected back to
the fee account page with *"This fee agreement already has an installment
plan."* whenever a plan existed.

The database never objected: `fee_installments` rows are protected by
`ON DELETE RESTRICT` only from `payment_allocations`, and the unique key
is `(fee_account_id, installment_number)` — nothing prevented replacing
a schedule. The blocker was purely in the service layer, and it was
over-broad: the reason a schedule could not be replaced was never
*"it is a schedule"*, it was *"a payment has been booked against it"*.

So an institute that typed a wrong fee schedule had **no way out at
all**: not an edit, not a delete-and-recreate, nothing.

### 15.2 The rule that was implemented

| Situation | What is allowed |
|---|---|
| **Case A** — the plan has **no** payment-allocation history | The administrator may change installment amounts, change due dates, add rows, remove rows and correct the numbering. The replacement must still add up to the net fee exactly. |
| **Case B** — the plan **has** payment-allocation history | The replacement is refused. |

**Case B applies even when the payment was voided afterwards.** The
allocations of a voided payment are kept as audit history (that is the
existing, deliberate rule of the module), so the history still exists and
the schedule still has to match it. The refusal message is:

> This installment schedule cannot be edited because payment-allocation
> history exists. The original schedule and financial history have been
> preserved.

The rule is enforced in the **service layer** (`InstallmentService.replacePlan`),
not by hiding a button — the button is hidden as well, but that is only
a convenience.

### 15.3 Files created or modified

**Created**

| File | Purpose |
|---|---|
| `src/main/java/com/omsai/coaching/fees/ImmutablePlanException.java` | a `BusinessRuleException` subtype, so the controller can tell an immutable schedule (nothing the administrator typed can help) apart from an invalid submission (the form can be corrected) |
| `src/test/java/com/omsai/coaching/fees/InstallmentPlanEditTest.java` | 24 service tests |
| `src/test/java/com/omsai/coaching/web/InstallmentPlanEditAccessWebTest.java` | 13 HTTP and authorization tests |
| `src/test/java/com/omsai/coaching/browser/InstallmentPlanEditBrowserTest.java` | 3 real-Chromium tests |

**Modified**

| File | Change |
|---|---|
| `src/main/java/com/omsai/coaching/fees/InstallmentService.java` | `replacePlan(...)`, `isEditable(...)`, `scheduleOf(...)`, `scheduledTotal(...)`, the `IMMUTABLE_PLAN_MESSAGE` constant, and the class documentation |
| `src/main/java/com/omsai/coaching/fees/PaymentAllocationRepository.java` | `countForAccount(...)` — counts **every** allocation row, including those of a voided payment |
| `src/main/java/com/omsai/coaching/fees/FeeAccountController.java` | `GET`/`POST /fees/{id}/installments/edit`, the `planEditable` flag on the fee account page, the shared `installmentPlanModel(...)` helper |
| `src/main/java/com/omsai/coaching/fees/FeeAccountService.java` | `findWithDetails(...)` — a nullable lookup so a missing agreement is answered with a message instead of an error page |
| `src/main/resources/templates/fees/detail.html` | the *Edit installment plan* action and the explanation when the plan is immutable |
| `src/main/resources/templates/fees/installment-form.html` | the form now serves both modes: the current schedule is loaded, the current/proposed totals and the difference are shown, the submit button and the action change |
| `src/main/resources/static/js/installments.js` | the difference preview, and the money arithmetic moved from floating point to whole cents |
| `src/test/java/com/omsai/coaching/fees/InstallmentConcurrencyTest.java` | the concurrent payment/edit race test |

**No migration was needed and no existing migration was touched.** The
schema already allows a schedule to be replaced; only the service layer
was blocking it.

### 15.4 How the replacement works

The rows that keep their installment number are **updated in place** —
their identity, their creator and their creation date survive — the
surplus rows are removed and the missing ones are inserted. An
installment number is never moved, so the unique key
`(fee_account_id, installment_number)` can never be violated and the
Hibernate flush order (inserts before deletes) cannot cause a collision.

The fee agreement itself is never touched: gross fee, discount and net
fee are read-only in this workflow, and the administrator cannot change
them through the form.

All the validation of §4 of the repair brief is enforced by the
**existing** `InstallmentService.parse(...)` and total check, so the
creation rules and the editing rules cannot drift apart:

1. at least two installments;
2. every amount positive;
3. at most two decimal places (and the amount fits the column);
4. numbers sequential from 1 through N, no gaps, no duplicates;
5. a due date on every row;
6. the sum equals the **authoritative** net fee exactly — the server
   never adjusts an amount to make the schedule balance;
7. the replacement belongs to the fee account of the URL;
8. an invalid submission leaves the existing schedule unchanged (the
   whole operation is one transaction and nothing is written before the
   validation passes).

Money is `BigDecimal` end to end; the JavaScript preview now also works
in whole cents, so the preview can never disagree with the server by a
rounding error — and the server stays authoritative either way.

### 15.5 How the allocation history is protected

Three layers, all verified:

1. **Service layer** — `replacePlan` counts the allocation rows of the
   account inside the transaction and refuses when the count is not
   zero. The count uses the `fee_account_id` column, which the composite
   foreign keys guarantee to be the account of the referenced
   installment.
2. **Database layer** — `payment_allocations.installment_id` is
   `ON DELETE RESTRICT`, so the rows of a plan that *does* have
   allocations could not physically be deleted even if the service were
   bypassed.
3. **UI layer** — the edit action is not offered and the reason is shown.

Nothing in the refused path touches a payment, a receipt number, an
allocation, a void record or the fee agreement. The tests assert this
explicitly (§15.7).

### 15.6 How a concurrent payment and a concurrent edit are handled

Both operations take **the same pessimistic lock on the fee-account row**
(`FeeAccountRepository.getForUpdate`, `PESSIMISTIC_WRITE`) that the
payment recording already uses:

```
PaymentService.record()   ─┐
InstallmentService.createPlan() ─┼─► SELECT … FOR UPDATE on fee_accounts
InstallmentService.replacePlan() ─┘
```

They therefore serialize. Two orderings are possible and both are safe:

* **The payment commits first** — the allocation now exists, and the
  edit finds it when it re-checks the history *inside* the lock and is
  refused with the explanation.
* **The edit commits first** — the payment is then allocated to the
  new rows, in the payment's own transaction.

There is no third outcome in which money is allocated to a row that no
longer exists, because the rows are only replaced while the lock is
held and while the allocation count is zero.

### 15.7 Tests executed for this repair

**Service layer — `InstallmentPlanEditTest` (24 tests, all pass)**

```
anAdministratorCanReplaceAPlanWithoutAllocationHistory      PASS
installmentAmountsCanBeCorrected                             PASS
dueDatesCanBeChanged                                         PASS
installmentsCanBeAdded                                       PASS
installmentsCanBeRemoved                                     PASS
theResultIsAlwaysNumberedSequentiallyFromOne                 PASS
aRenumberedScheduleKeepsOneRowPerNumber                      PASS
theFeeAgreementAndItsReceiptsAreUntouched                   PASS
theNewScheduleMustEqualTheNetFeeExactly                      PASS
aScheduleWithFewerThanTwoInstallmentsIsRejected              PASS
nonPositiveAmountsAreRejected                                PASS
amountsWithMoreThanTwoDecimalsAreRejected                    PASS
aMissingDueDateIsRejected                                    PASS
numberingWithGapsIsRejected                                  PASS
duplicateNumbersAreRejected                                  PASS
anInvalidEditLeavesTheOriginalScheduleCompletelyUnchanged    PASS
editingAPlanWithPaymentAllocationsIsRefused                  PASS
editingAPlanWhosePaymentWasVoidedIsAlsoRefused               PASS
aRefusedEditLeavesThePaymentsTheReceiptsAndTheAllocationsUntouched  PASS
aRefusedEditKeepsThePaidAmountOfTheInstallments              PASS
aPlanWithoutAllocationsIsEditable                            PASS
aPlanWithAllocationsIsNotEditable                            PASS
anAccountWithoutAPlanIsNotEditable                           PASS
theCurrentScheduleCanBeLoadedIntoTheForm                     PASS
```

**HTTP and authorization — `InstallmentPlanEditAccessWebTest` (13 tests, all pass)**

```
theAdministratorCanOpenTheEditFormOfAnEligiblePlan           PASS
theEditFormContainsTheCurrentSchedule                        PASS
theAdministratorReplacesTheScheduleThroughTheForm            PASS
anInvalidScheduleIsRejectedAndTheFormComesBackWithAMessage   PASS
theEditNeedsTheCsrfToken                                     PASS
anAdministratorIsRefusedWhenPaymentAllocationsMakeThePlanImmutable  PASS
aForgedEditOfAnImmutablePlanIsRefusedAndChangesNothing       PASS
theFeeAccountPageExplainsWhyTheScheduleCannotBeEdited        PASS
theFeeAccountPageOffersTheEditActionWhileThePlanIsEditable   PASS
teachersCannotOpenOrSubmitTheEditForm                        PASS
studentsCannotOpenOrSubmitTheEditForm                       PASS
anonymousVisitorsAreRedirectedToTheLoginPage                 PASS
anUnknownFeeAccountIsAnsweredWithAMessageAndNotAnErrorPage   PASS
```

**Real browser — `InstallmentPlanEditBrowserTest` (3 tests, all pass)**

```
theAdministratorEditsTheScheduleThroughTheRealInterface      PASS
anInvalidScheduleIsRejectedWithoutChangingTheOriginalPlan    PASS
theEditingActionDisappearsOnceAPaymentHasBeenBooked          PASS
```

**Concurrency — `InstallmentConcurrencyTest` (1 new test, all pass)**

```
aConcurrentPaymentAndScheduleEditCannotCorruptTheFinancialRecords  PASS
```

**Full suite**

| Suite | Before | After |
|---|---|---|
| `./mvnw -o test` | 277 | **315** (0 failures) |
| `./mvnw -o test -Pbrowser-tests` | 304 | **345** (0 failures) |
| `zsh scripts/e2e-smoke.sh` | 208 checks | **208 checks, 0 failed** |
| `git diff --check` | clean | clean |

### 15.8 Live verification against the running application

The development application was restarted with the new code and the real
development database checked over HTTP. All 17 fee agreements that carry
a plan in `omsai` also carry allocations (three each), so every one of
them is immutable — which is exactly the real-world case:

```
GET /fees/15
  'Edit installment plan' link present: False
  explanation present: True

GET /fees/15/installments/edit -> HTTP 302 redirect=/fees/15
  flash: "payment-allocation history exists. The original schedule and
          financial history have been preserved."
```

The editable path was verified through the Playwright suite against a
real Chromium and the real application.

### 15.9 Data integrity after the repair

| Check | Result |
|---|---|
| `omsai` — orphan installments | **0** |
| `omsai` — orphan allocations | **0** |
| `omsai` — installments / allocations / payments / fee accounts | 34 / 51 / 80 / 31, unchanged in kind, no financial record touched |
| `omsai_test` — residue after the whole suite | **0** installments, 0 allocations, 0 fee accounts |
| Existing development data | untouched — no plan, payment, allocation, receipt or fee agreement was modified or deleted; the only database change of the whole repair is **none at all** (no migration was needed) |

### 15.10 Remaining limitations of this repair

* **A schedule that has money booked against it can never be corrected.**
  That is the deliberate rule of this task, not an oversight: correcting
  it would need installment rescheduling and financial adjustments,
  which are explicitly out of scope. The institute's way out today is to
  void the payment — and even then the schedule stays immutable, because
  the voided payment's allocations are kept as audit history.
* **The whole plan is replaced, not individual rows.** A single wrong
  due date means re-entering the schedule; the amounts and due dates of
  the untouched rows are pre-filled, so this is a correction, not a
  re-entry.
* **The preview is a convenience.** The browser shows the proposed total
  and the difference to the net fee, but the server is the only
  authority.
* **A pre-existing defect was found but deliberately not fixed here:**
  a *missing* fee account raises a `BusinessRuleException` that no
  global handler catches, so **every** fee page (`/fees/{id}`,
  `/fees/{id}/edit`, `/payments/{id}/receipt`, …) answers a guessed id
  with a 500 error page. The new edit endpoints answer it cleanly
  (a redirect with a message), but the other pages were left alone —
  a global handler would change the behaviour of every module and is
  not part of this task. It is recorded in the backlog below.

---

## 16. THE THIRD REPAIR — P1-3: SUBJECT LIFECYCLE, AND THE CURATED DEMO DATASET

> Implemented 2026-10-21, after §10 (P1-1) and §15 (P1-2). The scope is
> the subject lifecycle and the demonstration dataset; no other audit
> finding was started.

### 16.1 Root cause of the incomplete subject management

`SubjectService` could create, rename and describe a subject — and that
was all. There was no way to take an obsolete subject out of the offer
and no way at all to remove one that was never used:

```java
// Before: no lifecycle method existed on the subject.
public Subject create(SubjectForm form) { ... }
public Subject update(Long id, SubjectForm form) { ... }
public List<CourseSubject> coursesOf(Subject subject) { ... }
```

The database never objected: `course_subjects.subject_id` is
`ON DELETE RESTRICT`, so a subject that a course includes cannot be
deleted physically — but **no service ever explained that**. The
consequences:

* a subject an administrator no longer teaches stayed in the picker of
  every course page and could still be assigned to new courses;
* a typo in a subject name could be corrected, but a subject that
  should not exist at all could not be removed;
* an administrator who tried to delete a used subject got the same
  opaque database error that the batch-subject repair (§10.2) had.

The block was purely in the service layer, and it was over-broad in the
same way the batch-subject block was: the real reason a subject must not
be deleted is *"a course already includes it"*, not *"it is a subject"*.

### 16.2 The rule that was implemented

| Situation | What is allowed |
|---|---|
| **Case A** — no course includes the subject | It may be **deleted** (nothing depends on it), and it may also be deactivated like any other subject. |
| **Case B** — a course already includes the subject | It may **not** be deleted. It is **deactivated** instead: every course relationship, batch, class and result stays exactly where it is, and the subject leaves the pickers so it cannot be assigned to a new course. |
| **Case C** — reactivation | A deactivated subject is activated again. No relationship is recreated and nothing is duplicated. |

The refusal message (service layer, shown on the subject page):

> This subject is part of 2 courses, so it cannot be deleted without
> losing the teaching history of those courses. Deactivate it instead —
> it will then no longer be offered for new courses, while the existing
> courses, batches and classes stay exactly as they are.

### 16.3 Files added and modified

**Created**

| File | Purpose |
|---|---|
| `src/main/resources/db/migration/V8__subject_lifecycle.sql` | `subjects.active` + index (additive; existing rows stay active) |
| `src/main/java/com/omsai/coaching/demo/DemoCatalog.java` | the curated catalogue: 18 subjects, 31 courses with their subject mappings, 9 teachers, 18 students |
| `src/main/java/com/omsai/coaching/demo/DemoDataSeeder.java` | the opt-in, idempotent, guarded seeder |
| `src/test/java/com/omsai/coaching/subject/SubjectLifecycleTest.java` | 23 service tests |
| `src/test/java/com/omsai/coaching/web/SubjectLifecycleAccessWebTest.java` | 10 HTTP and authorization tests |
| `src/test/java/com/omsai/coaching/browser/SubjectLifecycleBrowserTest.java` | 5 real-Chromium tests |
| `src/test/java/com/omsai/coaching/demo/DemoCatalogTest.java` | 15 catalogue tests |
| `src/test/java/com/omsai/coaching/demo/DemoDataSeederTest.java` | 17 seeder tests |
| `scripts/demo-data-cleanup-report.sh` | the **read-only** cleanup-candidate report |

**Modified**

| File | Change |
|---|---|
| `src/main/java/com/omsai/coaching/subject/Subject.java` | the `active` flag |
| `src/main/java/com/omsai/coaching/subject/SubjectRepository.java` | `getForUpdate` (pessimistic lock), `findByFullNameIgnoreCase` |
| `src/main/java/com/omsai/coaching/subject/SubjectService.java` | `delete`, `setActive`, `isActive`, `courseCount` |
| `src/main/java/com/omsai/coaching/subject/SubjectController.java` | `POST /subjects/{id}/status`, `POST /subjects/{id}/delete` |
| `src/main/java/com/omsai/coaching/batch/CourseSubjectRepository.java` | `countForSubject`; the assignable picker only offers **active** subjects |
| `src/main/java/com/omsai/coaching/course/CourseService.java` | refuses to assign an inactive subject |
| `src/main/java/com/omsai/coaching/config/AppProperties.java` | the `app.demo.*` properties |
| `src/main/resources/application.yml` | `app.demo.enabled` (default **false**) and `app.demo.target-database` |
| `src/main/resources/application-prod.yml` | `app.demo.enabled` **pinned to false** |
| `src/main/resources/templates/subjects/detail.html` | the status badge, the delete/deactivate actions and the explanation |
| `src/main/resources/templates/subjects/list.html` | the status column |
| `src/main/resources/templates/courses/detail.html` | a *Deactivated* marker on the subjects a course already includes |
| `src/main/java/com/omsai/coaching/teacher/TeacherRepository.java` | `findByFullNameIgnoreCase` (idempotence) |
| `src/main/java/com/omsai/coaching/student/StudentRepository.java` | `findByFullNameIgnoreCase` (idempotence) |
| `src/test/resources/application-browser.yml` | the browser profile carries the demo catalogue |

**No earlier migration was modified.** V8 is new and additive.

### 16.4 How the deletion is protected

Three layers, all verified:

1. **Service layer** — `SubjectService.delete` locks the subject row
   (`SELECT … FOR UPDATE`) and counts the course assignments before
   deleting anything. The lock also blocks the foreign-key check of a
   concurrent `INSERT` into `course_subjects` (PostgreSQL takes a
   `FOR KEY SHARE` lock on the referenced row), so the check cannot
   become stale.
2. **Database layer** — `course_subjects.subject_id` is
   `ON DELETE RESTRICT`: a subject a course includes could not be
   deleted physically even if the service were bypassed.
3. **UI layer** — the delete action is only offered for a subject that
   no course uses; for a used subject the page explains why and offers
   the deactivation instead.

Deactivation never touches a relationship: the course-subject links, the
batch-subject assignments, the class sessions, the attendance marks and
the examination results all stay exactly where they are.

### 16.5 The curated course catalogue

**18 subjects** (reused, never duplicated): Mathematics, Mathematics
Part I — Algebra, Mathematics Part II — Geometry, General Science,
Science and Technology, Physics, Chemistry, Biology, English, Marathi,
Hindi, History and Civics, History and Political Science, Geography,
Computer Studies, Information Technology, Engineering Mathematics,
Applied Mathematics.

**31 course offerings** in seven categories:

| Category | Offerings | Subjects |
|---|---|---|
| School Tuition (8) | Standard 7 & 8 — Maharashtra State Board Tuition; Standard 7 — Mathematics Foundation; Standard 8 — Mathematics and Science Foundation; Standard 9 — Maharashtra SSC Tuition; Standard 10 — Maharashtra SSC Board Preparation; Standard 9 — Mathematics and Science; Standard 10 — Mathematics and Science Board Preparation | the full middle-school and SSC sets for the full tuition, only Mathematics + General Science for the two foundation offerings |
| Senior Secondary (7) | Standard 11 and 12 Science — PCM / PCB / PCMB, and Standard 12 Science — Board Examination Preparation | Physics, Chemistry, Mathematics, Biology, English as the stream requires |
| MHT-CET Preparation (5) | PCM and PCB Target Batch, PCM and PCB Crash Course, Class 11 and 12 Combined Preparation | Physics, Chemistry, Mathematics, Biology as the combination requires |
| JEE Preparation (2) | JEE Main — PCM Preparation; JEE Main and Advanced — PCM Preparation | Physics, Chemistry, Mathematics |
| NEET Preparation (2) | NEET-UG — Medical Entrance Preparation; NEET-UG — Intensive Revision | Physics, Chemistry, Biology |
| Foundation Program (2) | JEE Foundation — School-Level Preparation; NEET Foundation — School-Level Preparation | Physics, Chemistry, Mathematics / Biology |
| Engineering Mathematics (5) | Engineering Mathematics I–IV; Applied Mathematics — University/Branch-Specific Coaching | Engineering Mathematics / Applied Mathematics |

**Deliberate decisions**

* **Botany and Zoology are not separate subjects.** The data model has
  no topic or module entity — a course maps to a flat list of subjects —
  so a separate Botany *and* Zoology subject would have made a course
  list the same content twice. Biology carries both parts in its
  description instead.
* **The foundation courses are not an examination category.** Their
  category is *Foundation Program* and their description says they are
  an offering of the institute, not an official JEE or NEET category.
* **The engineering courses claim no universal syllabus.** Every one of
  them says that the scheme follows the affiliated university and
  differs between universities and branches. No chapter-level syllabus
  was invented.
* **No price, no fee, no teacher qualification and no examination
  guarantee** appears anywhere in the catalogue.

### 16.6 The demo identities and how they are protected

9 teachers and 18 students with plausible Maharashtrian names. They are
**fictional demonstration identities**, not customers and not staff.

| Protection | How |
|---|---|
| No real phone number | Indian mobile numbers always start with 6–9; every demo record uses a number starting with `00` (e.g. `0000000001`), which cannot be a mobile line and can never be dialled. |
| No real mail address | Every teacher address uses the reserved domain `example.invalid` (RFC 2606), which can never resolve, so **no message can leave the machine**. |
| No sign-in accounts | The demo teachers and students have **no login account**, so no password is created and none is published. The administrator account is never touched. |
| No financial or academic history | The seeder creates **no** fee agreement, payment, receipt, installment, class session, attendance mark or examination result. The dashboard must not look busy because money and marks were invented. |
| The existing demonstration students are untouched | *Aarav Sharma* and *Dia Patel* are **not** in the catalogue and were not created again, overwritten or reused. |

The demo records are only created in a database the operator names
explicitly (§16.7) — never in `omsai` by accident and never in
production.

### 16.7 The seeder: design and how to run it

`DemoDataSeeder` is a `CommandLineRunner` that writes the catalogue and
nothing else. **Three independent guards** have to agree before a single
row is written:

| Guard | Behaviour |
|---|---|
| `app.demo.enabled` | false by default; the production profile **pins it to a literal `false`**, so even `DEMO_DATA_ENABLED=true` in the environment has no effect on the `prod` profile. |
| the `prod` profile | the seeder refuses when the profile is active, whatever the flag says. |
| `app.demo.target-database` | must equal the name of the database the application is actually connected to (`select current_database()`), so the operator has to state the target explicitly. |

**How to run it** — for a dedicated demo environment:

```bash
createdb omsai_demo          # or: psql -c 'CREATE DATABASE omsai_demo'
DB_URL=jdbc:postgresql://127.0.0.1:5433/omsai_demo \
DEMO_DATA_ENABLED=true \
DEMO_DATA_TARGET_DATABASE=omsai_demo \
SERVER_PORT=8091 \
./mvnw spring-boot:run
```

The seeder logs what it did:

```
Demo dataset: seeding the curated catalogue into 'omsai_demo'.
Demo dataset: done. Created 18 subject(s), 31 course(s), 9 teacher(s),
18 student(s), 31 batch(es) and 36 enrollment(s). Already present: 0 …
```

**Idempotence.** Every record is looked up by its natural key before it
is written: a subject and a course by their name, a teacher and a
student by their name, a batch by course + year + name, a
batch-subject assignment by batch + course-subject, an enrollment by
student + batch + status. A second run therefore creates **nothing** and
changes nothing. Verified live:

```
first run : Created 18 subject(s), 31 course(s), 9 teacher(s), 18 student(s), 31 batch(es) and 36 enrollment(s).
second run: Created 0 subject(s), 0 course(s), 0 teacher(s), 0 student(s), 0 batch(es) and 0 enrollment(s).
             Already present: 18 subject(s), 31 course(s), 9 teacher(s), 18 student(s), 31 batch(es) and 36 enrollment(s).
```

The seeder uses the **existing service layer** (`SubjectService`,
`CourseService`, `BatchService`, `TeacherService`, `StudentService`,
`EnrollmentService`), so every business rule — the unique names, the
active course for a batch, the one active enrollment per student and
course — applies to the demo data as well. No raw SQL insert is used.

### 16.8 Existing data: what was preserved, and the cleanup candidates

**Nothing was deleted, truncated, reset or cleaned.** The development
database `omsai` was not touched by this work at all; the only change
it received is the additive `V8` migration.

The records that must be kept are still there and were verified
individually:

| Record | State |
|---|---|
| Students *Aarav Sharma* (`OMS-2026-00001`) and *Dia Patel* (`OMS-2026-00002`) | untouched — not duplicated, not overwritten, not reused |
| Course *Class 10 - CBSE*, batches *Morning Batch A* / *Morning Batch B* | untouched |
| Subjects *Mathematics*, *Physics* | untouched |
| The administrator `admin@omsai.local` | untouched |
| All 32 fee agreements, 83 payments, 54 allocations, 127 class sessions, 62 attendance rows, 10 timetable entries, 22 examinations, 161 enrollments | untouched |

**The cleanup candidates are reported, not removed.** The read-only
report `scripts/demo-data-cleanup-report.sh` lists them with the number
of records that depend on each one:

```
$ zsh scripts/demo-data-cleanup-report.sh
--- 2. Candidates: courses -------------------------------------------
6 | DBg Course 1792491590 | subjects=1 | batches=0 | enrollments=0
11 | FF Course 1792492877 | subjects=1 | batches=0 | enrollments=0
12 | FG Course A 1792493073 | subjects=1 | batches=0 | enrollments=0
...
--- 7. Records that are NOT candidates (must be kept) ----------------
students: 2 | teachers: 0 | courses: 9 | subjects: 3 | batches: 2 | accounts: 1
--- 8. The summary of the dependent records of the candidates --------
fee_accounts: 32 | payments: 83 | payment_allocations: 54
class_sessions: 127 | attendance: 62 | timetable_entries: 10
examinations: 22 | enrollments: 161
```

The seven `DBg/FF/FG Course …` rows and the `Debug Subject` are
**unidentified** — they were not written by the committed smoke script
and their provenance cannot be established from the repository, so they
are only *reported*, never classified as disposable. Everything with
the `E2E` prefix is clearly written by `scripts/e2e-smoke.sh`.

A removal needs its own reviewed script, a backup taken with
`scripts/backup-db.sh` and an explicit decision per record. **No such
script was written and nothing was deleted.**

### 16.9 Tests actually executed

| Suite | Before | After |
|---|---|---|
| `./mvnw -o test` | 315 | **380** (0 failures) |
| `./mvnw -o test -Pbrowser-tests` | 345 | **415** (0 failures) |
| `zsh scripts/e2e-smoke.sh` | 208 checks | **208 checks, 0 failed** |
| `git diff --check` | clean | clean |

New tests: 23 subject-service + 10 subject-HTTP + 5 subject-browser +
15 catalogue + 17 seeder = **70**.

| Requirement | Test |
|---|---|
| Deletion of an unused subject | `anUnusedSubjectCanBeDeleted` |
| Refusal to delete a referenced subject | `aSubjectUsedByACourseCannotBeDeleted` |
| Safe deactivation of a referenced subject | `aReferencedSubjectCanBeDeactivated` |
| Exclusion from the assignment selector | `anInactiveSubjectIsNotOfferedForANewCourseAssignment`, `anInactiveSubjectCannotBeAssignedToACourse` |
| Continued visibility of the relationships | `deactivatingKeepsTheCourseRelationshipAndItsHistory`, `anInactiveSubjectKeepsItsExistingCourseAssignment` |
| Reactivation without duplicates | `aDeactivatedSubjectCanBeReactivated`, `reactivatingDoesNotRecreateAnyRelationship` |
| Unauthorized access | `teachersCannotUseTheLifecycleActions`, `anonymousVisitorsAreRedirectedToTheLoginPage` |
| Repeated actions and invalid transitions | `deletingTheSameSubjectTwiceDoesNotSucceedTwice`, `deactivatingAnAlreadyDeactivatedSubjectIsRefused`, `activatingAnAlreadyActiveSubjectIsRefused` |
| Preservation of dependent records | `aRefusedDeletionLeavesTheSubjectAndItsCourseRelationshipIntact` |
| Regression for the course management | `anExistingSubjectCanStillBeAssignedToACourse`, `theSameSubjectCannotBeAssignedTwiceToTheSameCourse`, `aSubjectNameIsStillUnique`, `aCourseCanStillBeCreatedAndAssignedSubjects` |
| Every intended course can be seeded | `theSeederCreatesEveryCatalogueCourse` |
| Required subjects per course | `everySeededCourseHasItsRequiredSubjects` |
| PCM / PCB / PCMB / JEE / NEET / Eng. Math mappings | `theSeededStreamCoursesHaveTheRightSubjects` |
| School courses without indiscriminate subjects | `theSchoolCoursesDoNotAttachEverySubject` |
| No duplicated subjects | `everySubjectIsDistinctAndProfessionallyNamed` |
| Professional names, no debug placeholders | `everyCourseIsDistinctAndProfessionallyNamed`, `everySubjectIsDistinctAndProfessionallyNamed` |
| Repeated seeding creates no duplicates | `aSecondRunCreatesNothingNew` |
| Existing demonstration students untouched | `theExistingDemonstrationStudentsAreNotRepeated` |
| Synthetic contact details | `theSeededContactDetailsAreSynthetic`, `theSyntheticContactDetailsCannotReachAnybody` |
| Production cannot enable the seeding | `theSeederRefusesInTheProductionProfile` (plus the literal `false` in the prod profile, verified live) |
| Unrelated data unchanged | `theSeederLeavesExistingUnrelatedDataAlone` |
| No financial history created | `theSeederCreatesNoFinancialOrAcademicHistory` |

### 16.10 Remaining limitations

* **A subject that is already taught can never be deleted.** That is the
  deliberate rule of this task: the course relationships are history.
  Deactivation is the way out.
* **The whole plan is not the subject** — a subject can only be
  deactivated as a whole; there is no per-course "hide this subject
  from this course".
* **The demo catalogue is a starting point, not a finished syllabus.**
  The subject descriptions are one line each; no chapter-level syllabus
  exists, and none was invented.
* **The seeder writes to one named database per start.** A second
  demonstration environment needs its own database (and its own
  `DEMO_DATA_TARGET_DATABASE`).
* **The cleanup candidates are still in the development database.** They
  are reported, not removed — that decision is still open.

---

## 17. CORRECTION — WHY THE CURATED DATASET WAS NOT VISIBLE, AND THE FIX

> 2026-10-21, after §16. The previous report was **technically true but
> practically wrong**: the catalogue had been seeded, but the
> application the client opens did not use that database. This section
> records the diagnosis with evidence and the repair.

### 17.1 The symptom

The application the client opens (the instance on port 8080) showed
the old E2E-generated and debug records — not the Maharashtra
catalogue, not the fictional Indian names — even though the seeder
had reported success and the tests were green.

### 17.2 The diagnosis, with evidence

**The datasource of the running application.** The instance on port
8080 was started with **no environment overrides at all**:

```
$ ps aux | grep CoachingErpApplication     →  no DB_URL, no SPRING_PROFILES_ACTIVE
```

so it ran the **default profile** and therefore the default datasource
of `application.yml`:

```
url: ${DB_URL:jdbc:postgresql://127.0.0.1:5433/omsai}
```

and the demo seeding was **off**, because `app.demo.enabled` defaults
to `false`:

```
Demo dataset: not seeded - app.demo.enabled is false. Nothing was written.
```

**The databases, measured:**

| Database | curated courses | E2E courses | debug courses |
|---|---|---|---|
| **`omsai`** — the running application | **0** | 84 | 8 |
| `omsai_demo` — seeded by the previous task | **31** | 0 | 0 |
| `omsai_browser` — the browser-test database | 31 | 0 | 0 |
| `omsai_test` — the unit-test database | 0 | 0 | 0 |

**The application pages, measured over real HTTP** (logged in as the
administrator, the list pages fetched):

```
=== COURSES: 20 on page 1 ===
    Applied Mathematics — University/Branch-Specific Coaching
    Engineering Mathematics I
    ...
```

— which is the *catalogue*, because that request went to the demo
instance. The same request against the instance the client was
actually opening returned the E2E and debug rows.

### 17.3 The root cause

**The curated dataset was in the wrong database for the interface, and
there was no documented way to launch the application against it.**

Three separate gaps added up:

1. **The demo seeder was opt-in but there was no demo *run mode*.**
   `app.demo.enabled` had to be set by hand, together with a matching
   `DB_URL` and `app.demo.target-database` — three independent
   environment variables that had to agree. Nothing in the project
   provided them as a unit, and the plain `./mvnw spring-boot:run`
   (the command in the README) starts the application against the
   **development** database with the demo seeding **off**.
2. **The previous task stopped at "the seeder works".** It verified
   the seeder against a scratch database and against the browser-test
   database, but never against the instance the client opens — so the
   acceptance criterion ("the user must see the data on the actual
   application pages") was not met.
3. **The demonstration database had no administrator**, so the demo
   instance could not even be signed into.

### 17.4 The repair

**A `demo` profile** (`src/main/resources/application-demo.yml`) that
bundles the three settings into one:

| Setting | Value |
|---|---|
| the datasource | `${DEMO_DB_URL:jdbc:postgresql://127.0.0.1:5433/omsai_demo}` |
| the seeder | `app.demo.enabled: true`, `app.demo.target-database: omsai_demo` |
| the administrator | created from the **environment** — no password in the repository |

**A launcher** (`scripts/start-demo-app.sh`) that makes the whole
thing one command:

```bash
zsh scripts/start-demo-app.sh
```

It refuses to run against any database other than `omsai_demo`
(so the development database can never be seeded by accident),
creates the demonstration database if it does not exist, tells the
operator whether an administrator already exists, and starts the
application with the demo profile on port 8080.

**The production protection is unchanged and was verified again:**
`application-prod.yml` still pins `app.demo.enabled` to a literal
`false`, and the seeder still refuses in the `prod` profile:

```
$ SPRING_PROFILES_ACTIVE=prod DEMO_DATA_ENABLED=true \
  DEMO_DATA_TARGET_DATABASE=omsai_demo … ./mvnw -o spring-boot:run
Demo dataset: not seeded - app.demo.enabled is false. Nothing was written.
```

**A browser test that verifies the real interface**
(`DemoCatalogueVisibleBrowserTest`). It is deliberately *not* built on
`BrowserTestBase`: it does **not** boot its own application and does
**not** use the `omsai_browser` database. It drives the instance that
is actually running (`DEMO_APP_URL`, default `http://127.0.0.1:8080`)
through a real Chromium, so the evidence is about the real pages and
not about a test double. It checks the course list, the subject list,
the course-subject mappings, the teacher list, the student list and
the absence of the old debug records, and it writes the screenshots to
`target/browser-artifacts/`.

### 17.5 The result, measured

The instance on port 8080 now runs the demo profile against
`omsai_demo`, and the pages show:

| Page | Before | After |
|---|---|---|
| **Courses** | 84 E2E + 8 debug rows | **the 31 curated offerings** ("Page 2 of 2 · 31 record(s)") |
| **Subjects** | 85 E2E/debug rows | **the 18 canonical subjects** |
| **Teachers** | 77 E2E rows | **the 9 fictional teachers** |
| **Students** | 84 E2E rows | **the 18 fictional students** |

Screenshots of the two populated pages:
`target/browser-artifacts/demo-catalogue-courses.png` and
`target/browser-artifacts/demo-catalogue-subjects.png`.

**After a restart** the counts are identical and the seeder reports
*"Created 0 …"* — the dataset survives a restart and re-seeding
creates no duplicates. Verified live:

```
before restart: courses=31 subjects=18 teachers=9 students=18 batches=31 enrollments=36 course_subjects=114
after  restart: courses=31 subjects=18 teachers=9 students=18 batches=31 enrollments=36 course_subjects=114
```

**The original `omsai` database was not touched at all:**

```
courses 93 | subjects 87 | batches 140 | students 86 | teachers 79 | enrollments 167
fee_accounts 33 | payments 86 | allocations 57 | sessions 132 | attendance 64
timetable 11 | exams 24 | accounts 58
curated courses in omsai: 0 | demo teachers in omsai: 0 | demo students in omsai: 0
Aarav Sharma OMS-2026-00001 Nashik 2026-04-01   ← unchanged
Dia Patel    OMS-2026-00002         2026-04-05   ← unchanged
```

Flyway history: 1 → 8, all successful.

### 17.6 The lesson for the remaining work

A seeder that is *correct* is not the same as a seeder that is
*visible*. Every remaining "the data must appear" requirement from now
on has to be checked **against the running application**, not against a
scratch database or a test double.

### 17.7 A second defect the verification found: the smoke test polluted the demo database

Running the required gate `zsh scripts/e2e-smoke.sh` against the demo
instance wrote its `E2E …` records into the **demonstration** database,
and they appeared in the visible catalogue:

```
e2e_courses=2 e2e_subjects=2 curated_courses=31
```

The new `DemoCatalogueVisibleBrowserTest` caught it — its assertion
*"the visible catalogue must not contain E2E records"* failed. That is
exactly what the test is for.

**Two repairs:**

1. **The smoke script now finds the application's database instead of
   assuming one.** It hardcoded `DB_NAME=omsai`, so its HTTP calls went
   to the demo application while its SQL queries went to the
   development database — 98 of the 208 checks failed. It now detects
   the database (the record it has just created through the running
   application is looked up in the candidates) and prints which one it
   found. Verified on both paths:

   ```
   demo instance on :8080  ->  == the running application uses the database 'omsai_demo'   208 checks, 0 failed
   dev  instance on :8091  ->  == the running application uses the database 'omsai'         208 checks, 0 failed
   ```

2. **The demonstration database is reset on every demo start**
   (`scripts/reset-demo-db.sh`, hard-guarded to `omsai_demo` only). The
   demonstration database is disposable — it holds nothing but the
   curated catalogue — so resetting it guarantees that what the client
   sees is genuinely the curated dataset and never the leftovers of a
   test run. The development database `omsai` is never touched.

After the reset the demonstration database is exactly the curated
dataset again:

```
e2e=0 debug=0 curated_courses=31 subjects=18 teachers=9 students=18
```
