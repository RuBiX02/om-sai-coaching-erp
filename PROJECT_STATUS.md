# PROJECT STATUS — Om Sai Coaching ERP, Phases 1–3 + attendance export/print + installment plans + client readiness audit + premium hybrid UI/UX redesign

Date: 2026-10-21
Scope: everything in the Phase 1 and Phase 2 specifications that was
actually built and verified, the Phase 3 real-browser verification and
CI setup, the attendance CSV export and printable attendance sheets, the
installment plans and automatic payment allocation added afterwards,
the **client readiness audit and the first repair** (the batch-subject
class lifecycle — see §13 and `CLIENT_READINESS_AUDIT.md`),
the **premium hybrid UI/UX redesign of the shared shell and the
administrator dashboard** (see §17),
and an honest account of gaps, limitations and the defects found (and
fixed) during development.

---

## 1. Bottom line

Phases 1–3 are implemented and work end to end:

- **`mvn test`: 380 tests, 0 failures, 0 errors, 0 skipped** (green)
  against the `omsai_test` PostgreSQL database — the 171 tests of
  Phases 1–3 plus 29 for the CSV exports and the printable sheet, 41
  for the installment module and 40 for the timetable, the examinations
  and the portals, plus the **133 new tests of the client readiness
  audit and repairs** (25 for the batch-subject class lifecycle in §13,
  38 for the installment-plan editing in §14, 70 for the subject
  lifecycle and the demo dataset in §15).
- **Real-browser Playwright suite: 35 tests, all pass** — see §4. A
  combined `mvn test -Pbrowser-tests` run executes **415 tests** (380 +
  35) in one go, all green. After the UI/UX redesign the same run
  executes **423 tests, 0 failures** (415 + 3 new design tests + the
  5 demo-catalogue checks, which really run when the demo instance is
  up) — see §17.5.
- **E2E smoke suite: 208/208 checks passed** (`zsh scripts/e2e-smoke.sh`
  against a locally running app) — the 127 checks of Phases 1–3 plus
  27 for the CSV exports and the printable sheet, 23 for the
  installment module and 31 for the timetable, the examinations and
  the student portal, all over real HTTP.
- **The final completion release works end to end in a real browser**:
  a recurring timetable is created, a double booking is refused, the
  classes are generated (idempotently), the teacher marks the attendance
  and enters the marks, the administrator publishes the results — and
  the student then sees only their own timetable, attendance, result and
  fee information (§7).
- **Installment plans and automatic payment allocation work end to end**
  (implemented and verified in this round — see §6): a schedule must
  add up to the net fee exactly, payments are allocated earliest due
  date first, money that was received before the plan is allocated to
  it, a voided payment stops counting without losing its audit trail,
  and the installment balances reconcile with the agreement.
- **Attendance CSV export and printable attendance sheets work end to
  end** (implemented and verified in this round — see §5): all four
  report types download a real CSV with the report's own filters, the
  numbers are the report service's, an undefined percentage stays empty
  instead of becoming a fake 0, and the printable sheet shows the
  eligible roster of the class date, prints without navigation and
  keeps the recorded marks.
- **Browser-level print verification succeeded**: Chromium print-media
  emulation and `page.pdf()` output parsed with PDFBox confirm receipts
  hide page controls, render the VOIDED stamp on voided payments and
  carry the correct receipt text (§4, §10).
- All reports above are from real runs on this machine, **not estimates
  from CI**. CI is configured in `.github/workflows/ci.yml` but has not
  been executed (no remote configured — see §11 limits).
- Phase 3 introduced a third, disposable database `omsai_browser` for
  the browser suite. `omsai` and `omsai_test` were never touched by it;
  no database was reset, dropped or reseeded (`scripts/reset-browser-db.sh`
  exists for operators and refuses anything but `omsai_browser`).

---

## 2. Feature status

### Phase 1 (all verified, unchanged by Phase 2)

Authentication and accounts; the anonymous/TEACHER/ADMIN access matrix;
courses, subjects, batches, batch subjects, teacher assignments;
referential-safety of removals; students with generated `OMS-2026-#####`
codes; append-only enrollment history with same-course transfers,
withdraw/complete and DB-enforced uniqueness; admin/teacher user
management with teacher-profile linking; dashboard with live counts;
login rate limiting (single-node, in-memory); validation feedback on
every form; friendly error pages; strict CSP security headers.

Phase 2 deliberately did not alter these flows. The only Phase 1 test
adjustment required was `DashboardServiceTest`: the Phase 2 schema now
enforces `ck_enrollments_closed_has_end` (a closed enrollment must have
an end date), so the test fixture stamps `end_date` when closing an
enrollment — a test fix, not a behavior change.

### Timetable, examinations and the portals — fully working (verified)

- **Recurring timetable** with server-side conflict detection (teacher,
  batch and classroom), calendar exceptions (holidays, cancelled and
  rescheduled occurrences) and an idempotent generation of class
  sessions — see §7.
- **Examinations and results** with a roster taken from the class on the
  examination date, validated marks, a draft/published workflow and
  academic reports — see §7.
- **Three portals**: the management dashboard (ADMIN), the teacher
  dashboard (TEACHER) and the student portal (STUDENT), each restricted
  to its own scope — see §7.

### Installment plans and payment allocation — fully working (verified)

- **Installment plans** divide the agreed net fee of one fee agreement
  into scheduled amounts with due dates; the agreement stays the
  authoritative record of the obligation.
- **Automatic allocation** of every payment, earliest due date first,
  with the installment number as the tie-breaker; payments that were
  received before the plan was created are allocated to it as well.
- **Derived statuses** (UPCOMING, DUE_TODAY, PARTIALLY_PAID, OVERDUE,
  PAID) computed from the due date, the effective allocations and the
  business date.
- **Upcoming, overdue and overview screens** plus three dashboard
  figures, all administrator-only — see §6.

### Attendance export and printing — fully working (verified)

- **CSV export** of all four attendance reports (student, batch,
  subject, date), administrator-only, with the report's own filters,
  the report service's own numbers and safe file names — see §5.
- **Printable attendance sheet** per class session, following the
  class-session ownership rules, with the eligible roster of that class
  date and print rules in a served stylesheet — see §5.

### Phase 2 — fully working (verified)

**Class sessions** (`attendance` package + `class_sessions` table)

- A session is one class on one date for one batch-subject assignment;
  there is no recurring timetable, and the batch/course/subject context
  always derives from the assignment so records cannot drift.
- Times are optional; `session_date` + times carry a DB CHECK
  (`ck_class_sessions_time_range`) and a service guard: end time must be
  after start time.
- Lifecycle OPEN → COMPLETED or OPEN → CANCELLED. Records are never
  deleted: the session page and categories remain, only buttons clamp.
- A completed session rejects edits; a session that already has
  attendance rows cannot be cancelled (attendance is never deleted and
  must stay attached to a class that actually took place). Both rules
  verified by tests and over HTTP.
- Only administrators and the assigned teacher may manage a session.
  Teacher scope is enforced in the **service**
  (`AccessDeniedException` → 403 page), not by hiding links.

**Attendance** (`attendance` table)

- Ties to an enrollment, not to the student: `UNIQUE (class_session,
  enrollment)` in the DB; corrections update in place.
- Recording rules: the enrollment must belong to the session's batch and
  its effective period must cover the session date (`enrollment_date ≤
  session_date` and `end_date ≥ session_date`); therefore a batch
  transfer cannot attach attendance to the wrong batch's history.
- Statuses PRESENT/LATE/ABSENT/EXCUSED with per-row remarks.
- A session with attendance cannot be cancelled. Marking requires an
  OPEN session from a teacher; administrators can still correct
  attendance on completed sessions (teacher lockout verified).
- The attendance page pre-fills the roster with existing rows for
  corrections and shows who has not been marked.

**Reports** (`AttendanceReportService` + native SQL)

- **Per student**: one row per historical enrollment (transfer history
  stays visible), with per-enrollment counts and an overall percentage
  across all rows.
- **Per batch** and **per subject (within a batch)**: rows per student
  with eligible-session denominators recorded at report time.
- **Per date**: every session with recorded counts and status badges.
- Percentage = (present + late) / (present + late + absent) × 100;
  EXCUSED is excluded; a null percentage is shown (never a fake 0%) when
  nothing has been evaluated for that student.
- Denominators count only COMPLETED sessions whose date falls inside the
  enrollment's period (cancelled and still-open sessions never count).

**Fee agreements** (`fee_accounts` table)

- Agreement per (student, course, academic year), money as
  `NUMERIC(12,2)` via `BigDecimal`; `net = gross − discount` with the
  discount capped by a service rule and a DB CHECK; the academic year
  must use the `2026-27` format; the enrollment must be ACTIVE.
- Same-course batch transfers reuse the existing agreement
  transactionally through an `EnrollmentTransferObserver` (no duplicate
  agreements, no data loss).
- Once a payment is recorded, the gross fee and discount can no longer
  be changed (only the due date and notes); verified over HTTP.

**Payments** (`payments` table, append-only)

- Append-only history; voiding sets who/when/why and the void flow has
  its own printable, re-stamped receipt. Voiding requires a non-blank
  reason (validated server-side).
- Receipt numbers come from the DB sequence `payment_receipt_seq` in the
  form `OMS-RCP-2026-000001` — never from counting rows: duplicate voids
  and void-outs never change the counter, and receipt numbers strictly
  increase even after voids.
- Overpayments are rejected with the exact outstanding balance;
  outstanding is computed server-side; concurrent recording of two
  payments against the same account is serialized by a pessimistic lock
  on the fee account (real-thread test: of two 800.00 payments against a
  1000.00 outstanding, exactly one is accepted).
- Printable receipt page in the browser (print-to-PDF; no PDF library).

**Teacher carve-out** (new in Phase 2)

- Teachers **can** create and manage class sessions and attendance for
  subjects assigned to their linked active teacher profile (enforced in
  the service: `AccessDeniedException` → 403 page, not just hidden
  links).
- Teachers are **barred from all fee, payment and report pages**. Every
  URL is enforced server-side: the URL-level role rule plus service
  rules for session management. Verified by 403 checks in JUnit and the
  smoke suite.

**UI integration**

- `students/{id}` detail page links to the attendance report (profile
  card) and fee-agreement pages (per ACTIVE enrollment row).
- Dashboard now has Phase 2 stat cards (sessions today, month-to-date
  collections, outstanding dues, receipt totals) and a "Class sessions
  today" table — all from DB queries.
- Sidebar adds Sessions, Fees, Payments and Reports entries (admin and
  teacher scoping respected); the payment-receipt page exposes a print
  button through a small progressive-enhancement hook.

### Working but with documented limits

- **Login rate limiting**: unchanged from Phase 1 (in-memory, one node).
- **Reports date ranges**: the batch/subject/student dashboards compute
  against report time; each render mirrors live data — there is no
  snapshot/audit history of previously generated reports.

### Not implemented (deliberately out of scope across Phases 1–2)

- No REST API endpoints; no React/SPA; no timetable or recurring
  schedule of classes; no examinations/grades; no student/parent
  portals; no payroll; no multi-branch support.
- No email/SMS notifications; no file uploads; no CSV/Excel import or
  export; no PDF-generation library (receipts are browser-print).
- No invoices or partial-refund workflows beyond voiding.

### Not verified

- ~~**Visual/browser rendering**~~ — closed in **Phase 3**: the screens
  are now exercised in real headless Chromium (Playwright for Java);
  see §4 and §10. What *remains* unverified is WebKit/Firefox rendering
  (single-engined Chromium only) and a physical printer's native print
  dialog (the `@media print` code path *is* verified via emulation and
  `page.pdf()`).
- **Docker/Testcontainers**: still unavailable here; tests run against a
  real local PostgreSQL (see README §1).
- **Multi-node behavior** of the in-memory rate limiter (documented
  Phase 1 limit).

---

## 3. Database migrations

- `V1__init.sql` — unchanged Phase 1 schema (never edited).
- `V2__attendance_and_fees.sql` — Phase 2 schema: `class_sessions`,
  `attendance`, `fee_accounts`, `payments` tables; `payment_receipt_seq`
  sequence; CHECK constraints (time range, non-negative money, net
  equals gross minus discount, discount ≤ gross, closed enrollments have
  end dates); unique indexes (`ux_attendance_session_enrollment`,
  `ux_fee_accounts_student_course_year`, `ux_payments_receipt_number`);
  extra index `ix_enrollments_batch_start_end` for roster queries;
  `ON DELETE RESTRICT` everywhere in the new foreign keys.
- `V3__installment_plans.sql` — the installment module: the
  `fee_installments` and `payment_allocations` tables with the
  constraints listed in §6 (unique per account and number, unique per
  payment and installment, positive amounts, composite foreign keys
  that keep a payment and an installment in the same fee account,
  `ON DELETE RESTRICT` everywhere) plus the two composite unique keys
  the allocation table needs. No existing table, row or column was
  dropped or rewritten.
- `V4__timetable.sql` — the timetable: `timetable_entries` (the
  assignment, the weekday, the times, the classroom, the effective
  period, active flag) and `calendar_exceptions` (holidays, cancelled
  and rescheduled occurrences). Unique/check constraints, indexes for
  the day and the classroom, `ON DELETE RESTRICT`.
- `V5__examinations.sql` — the examinations: `examinations` (the
  assignment, the title, the date, the maximum marks, the status) and
  `examination_results` (the examination, the **enrollment**, the
  status, the mark, the publication and correction audit). One result
  per examination and enrollment; a mark only for a present student.
- `V6__student_portal.sql` — the STUDENT role and the account link:
  the role CHECK of V1 (`user_accounts_role_check`) is replaced by one
  that allows `STUDENT`, and `students.user_account_id` is added with a
  unique index — the link is one-to-one in the database.
- `V7__batch_subject_lifecycle.sql` — the client-readiness repair
  (§13): `batch_subjects.active`, so a batch-subject assignment (the
  entity that makes a class) can be **deactivated** instead of deleted
  once it carries classes, a timetable entry or examinations. Additive
  only — existing rows stay active, no column or row was dropped or
  rewritten, and the `ON DELETE RESTRICT` foreign keys that protect
  the history are untouched.
- No data backfill was needed: Phase 1 data has no attendance/fees.
- Applied to `omsai` (dev) on 2026-10-21 and, by the same Flyway run,
  to `omsai_test` and `omsai_browser`; Flyway validate passes at boot
  against all three, and the fresh-database path was re-verified on
  `omsai_test`. The history of `omsai` reads 1 → 7, all successful.

---

## 4. Tests actually executed

JUnit suite (`./mvnw test`, run against `omsai_test`):

**Phase 1 suites (98 tests, all pass — unchanged except
`DashboardServiceTest`, see §2):**

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

**Phase 2 suites (44 tests, all pass):**

| Suite                        | Tests | What it proves |
|------------------------------|-------|----------------|
| ClassSessionServiceTest      | 11    | session create/edit rules, time validation, batch/course active checks, teacher ownership (403s), no-profile teacher denied, roster period filtering |
| AttendanceServiceTest        | 7     | upsert of one row per (session, enrollment), roster eligibility windows, teacher correction lockout on completed sessions, percentage formula incl. EXCUSED exclusion and null-on-empty |
| AttendanceReportServiceTest  | 4     | denominators count only completed sessions inside each enrollment period, EXCUSED does not lower the percentage (and produces no fake 0), transfer history appears as separate rows with correct periods, date report |
| FeeAccountServiceTest        | 9     | agreement creation rules (ACTIVE enrollment, duplicate per student+course+year, year format, discount ≤ gross), edit lock once payments exist, same-course transfer repoints the agreement into one transaction, outstanding math |
| PaymentServiceTest           | 5     | recording (balance, overpay rejection, validation), receipt sequence behavior, void with who/when/why, already-voided rejection, outstanding recalculation |
| PaymentServiceConcurrencyTest| 1     | real threads racing two payments against one balance with a pessimistic lock — exactly one accepted, outstanding correct; tests clean up their committed fixtures in FK order |
| PhaseTwoAccessWebTest        | 7     | anonymous redirects; CSRF enforcement on POST /sessions; teacher allowed on session pages but 403 on every fee/payment/report URL; teacher 403 creating sessions for an unassigned subject + 302 for an owned one; admin reaches every Phase 2 page |

**Phase 3/4 suites (29 tests, all pass — CSV export and printable
attendance sheet):**

| Suite                        | Tests | What it proves |
|------------------------------|-------|----------------|
| CsvWriterTest                | 8     | the CSV writer: header row, quoting of separators/quotes/line breaks, doubled embedded quotes, formula neutralisation (`=`, `+`, `-`, `@`), ordinary values untouched, `null` becomes an empty field |
| AttendanceExportWebTest      | 13    | every report type downloads a real CSV with a safe file name and the right header; the report filters are the download's filters; the numbers are the report service's (formula preserved); an undefined percentage is an empty cell and never 0; values with commas, quotes and line breaks are escaped; formula-like names are neutralised; an empty report still produces a valid header-only CSV; teachers and anonymous visitors get no export at all |
| AttendanceSheetWebTest       | 8     | an administrator and the assigned teacher can open the printable sheet, another teacher is denied (403); the sheet names course, batch, subject, date and teacher; only the eligible roster of that date is listed; recorded marks and remarks are visible; the print stylesheet is linked; the sheet carries no fee or payment information |

**Phase 5 suites (41 tests, all pass — installment module):**

| Suite                       | Tests | What it proves |
|-----------------------------|-------|----------------|
| InstallmentServiceTest      | 12    | a valid plan is created and changes nothing but the schedule; fewer than two installments, non-positive amounts, three-decimal amounts, a wrong total, duplicate plans and non-sequential numbers are all refused with precise messages; the plan belongs to the selected agreement; payments received earlier are allocated to the new schedule, voided ones are not; a fully paid agreement can be scheduled without inventing a payment; an invalid plan leaves no rows behind |
| InstallmentAllocationTest   | 8     | the earliest-due-first policy (a payment fills the first installment and spills into the next); the allocation total equals the payment; the payment keeps its amount and receipt number; an agreement without a plan behaves as before; voiding keeps the allocation but stops it counting and the balances come back; the installment balances reconcile with the agreement; a same-course batch transfer keeps the agreement, the plan and the allocations |
| InstallmentConcurrencyTest  | 3     | real threads: two simultaneous payments against a planned agreement cannot over-collect and leave no gap; a plan created while a payment is recorded never leaves the payment unallocated; a payment that cannot be allocated is rolled back completely (no payment, no allocation) |
| InstallmentReportTest       | 11    | the derived statuses (PAID, PARTIALLY_PAID, OVERDUE, DUE_TODAY, UPCOMING) including an overdue but partly paid installment and the documented precedence; exact-decimal balances; the upcoming report respects the selected dates and the student filter; the overdue report excludes fully paid and voided-only balances and counts the days late; the overview figures come from the database |
| InstallmentAccessWebTest    | 7     | the administrator reaches every installment screen, the plan form shows the agreement it divides, a plan is created through the form, a schedule that does not add up is refused with a message and writes nothing, a POST without a CSRF token is rejected, and teachers are forbidden from every URL and from the form submission while anonymous visitors are redirected to the login page |

**Final release suites (40 tests, all pass — timetable, examinations
and the portals):**

| Suite                       | Tests | What it proves |
|-----------------------------|-------|----------------|
| TimetableServiceTest        | 15    | a valid entry is created; an invalid time range or effective period is refused; a teacher, a classroom and a batch cannot be double booked; classes that touch but do not overlap are allowed; a different day or a non-overlapping effective period is no conflict; a deactivated entry frees its slot; the generation creates one session per occurrence, is idempotent, and skips holidays and cancelled occurrences; an existing class is never overwritten |
| ExaminationServiceTest      | 12    | an administrator creates a draft; the maximum marks are validated; a teacher may only manage the examinations of their own classes; only the students on the roster of the examination date are eligible; marks above the maximum, negative marks, a missing mark and a mark for an absent student are refused; an absent student has no mark at all; a result is stored only once; a draft result is invisible to the student until it is published; only an administrator publishes; a cancelled examination keeps its results; a result survives a transfer to another batch; the summary counts present, absent and excused separately and averages only the students who sat the examination |
| PortalAccessWebTest         | 13    | a student account is created and linked to its profile; a profile cannot be linked twice; a student account needs a profile; the student portal shows the own records only; a student cannot reach the management screens, change attendance/fees/marks or read another student's report; a teacher cannot reach the student portal and keeps the financial screens closed; an administrator cannot reach the student portal; anonymous visitors are redirected |

**Client-readiness audit suites (25 tests, all pass — the batch-subject
class lifecycle, see §13):**

| Suite                       | Tests | What it proves |
|-----------------------------|-------|----------------|
| BatchSubjectLifecycleTest   | 16    | an assignment that was never used can be removed; one with class sessions, a timetable entry or examinations is refused with a message naming the dependency and pointing at deactivation; a refused removal leaves the assignment and its classes untouched; the usage counts are exact; deactivating hides the assignment from every picker but keeps it visible on the batch page and keeps every existing class; reactivating puts it back; deactivating twice, activating an active one and activating inside an inactive batch are all refused with an explanation; adding a deactivated subject reactivates it instead of duplicating it, and it appears in the picker again |
| BatchSubjectAccessWebTest   | 9     | an administrator can deactivate, reactivate and remove; a refused removal comes back as a redirect with a readable message and deletes nothing; deactivating twice is refused with a message; a teacher is 403 on every batch URL **and** on both lifecycle POSTs; anonymous users are redirected to /login; the batch page still renders with the lifecycle action |

**Client-readiness repair 2 suites (38 tests, all pass — the
installment-plan editing, see §14):**

| Suite                       | Tests | What it proves |
|-----------------------------|-------|----------------|
| InstallmentPlanEditTest     | 24    | a plan without allocation history can be replaced; the amounts, the due dates, the number of rows and the numbering can all be corrected; the result is always numbered 1..n; the schedule must still add up to the net fee exactly; every invalid shape is rejected (fewer than two rows, a non-positive amount, three decimals, a missing due date, gaps, duplicates) and leaves the original schedule completely unchanged; a plan with allocations is refused, **also when its payment was voided**; a refused edit leaves the payments, the receipts, the allocations, the paid amounts, the schedule and the fee agreement untouched; an account without a plan is not editable; the current schedule can be loaded into the form |
| InstallmentPlanEditAccessWebTest | 13 | the administrator can open the edit form and sees the current schedule, the current total and the difference; a valid replacement is accepted; an invalid one comes back with a message and changes nothing; the CSRF token is required; a plan with allocations is refused on the direct URL and on a forged POST; the fee account page explains the refusal and offers the action while the plan is editable; **teachers and students are refused with 403 on the URL and on a forged POST**; anonymous visitors are redirected; an unknown fee account answers with a message, not an error page |

**Client-readiness repair 3 suites (70 tests, all pass — the subject
lifecycle and the demo dataset, see §15):**

| Suite                       | Tests | What it proves |
|-----------------------------|-------|----------------|
| SubjectLifecycleTest        | 23    | an unused subject is deleted; a subject a course includes is refused with an explanation and keeps its relationship; deactivating keeps every course relationship and every historical record; an inactive subject leaves the pickers and cannot be assigned to a course; a deactivated subject is activated again without recreating a relationship; deleting twice, deactivating twice and activating an active subject are all refused with a message; the existing course management still works (assign, duplicate refusal, unique name, create) |
| SubjectLifecycleAccessWebTest | 10  | an administrator deletes an unused subject, deactivates and reactivates a used one and is refused with a message on an invalid transition; the subject page offers the right action and explains the refusal; an inactive subject is no longer offered on the course page while the course keeps showing it; the CSRF token is required; **teachers are 403 on the URLs and on the forged POSTs**; anonymous visitors are redirected |
| DemoCatalogTest             | 15   | every course and subject is distinct and professionally named, with no debug placeholder; every course only uses catalogue subjects; the PCM / PCB / PCMB, JEE, NEET and engineering mappings are exactly right; the school courses do not attach every subject; the foundation courses are not advertised as an examination category; the engineering courses claim no universal syllabus; every subject is taught by a teacher and no teacher teaches everything; the existing demonstration students are not repeated; the contact details are synthetic and cannot reach anybody |
| DemoDataSeederTest          | 17   | the seeder is disabled by default and refuses without a named target database, with the wrong target database and in the production profile; a refused seeder writes nothing; every catalogue subject, course, teacher, student, batch and enrollment is created with the right mappings; **no fee agreement or payment is created**; the demo students have no sign-in account; a second run creates nothing and changes nothing; unrelated data is left alone |

**Total: 380 tests, 0 failures, 0 errors, 0 skipped.**

**Real-browser suite (35 tests, all pass; `mvn test
-Pbrowser-tests`, boots the app on a random port against the disposable
`omsai_browser` database and drives headless Chromium via Playwright for
Java 1.63.0):**

| Suite                       | Tests | What it proves |
|-----------------------------|-------|----------------|
| AuthenticationBrowserTest   | 4     | valid login reaches the dashboard topbar; wrong password shows "Invalid email or password."; protected pages redirect anonymous users to /login; logout lands on /login?logout and the session is really invalidated |
| CorePagesBrowserTest        | 3     | every sidebar link leads to the right management page with its topbar title; course form rejects an empty name and creates a valid course with the flash message; console stays free of errors |
| AttendanceBrowserTest       | 3     | admin creates a session, records per-student attendance, completes it and reads the student report with the exact counts/percentage; completed sessions reject teacher attendance and show the lockout notice, cancelled sessions refuse attendance; a teacher works only on their own assigned sessions, is blocked (403) from fees/payments/reports/users and never sees those sidebar links |
| FeesAndReceiptBrowserTest   | 3     | a partially paid agreement shows the recalculated "Outstanding balance"; the receipt shows amount, method and cheque reference and matches the sequence format `OMS-RCP-2026-######`; voiding marks ledger and receipt VOIDED with reason — verified on screen, under print emulation and via `page.pdf()` / PDFBox text extraction |
| InstallmentBrowserTest      | 5     | the administrator creates a plan through the form (including adding a row), a schedule that does not add up is refused with the message, a payment is allocated to the earliest installment and the fee account shows the paid and remaining amounts, the overview and the upcoming report show the student, voiding an allocated payment brings the balance back, and teachers are refused every installment screen and never see the link |
| InstituteWorkflowBrowserTest | 2     | the whole institute workflow in one browser: a recurring timetable is created, a double booking is refused with an explanation, the classes are generated (and a second run creates no duplicate), the teacher marks the attendance and completes the class, the teacher enters the marks of an examination, the administrator publishes the results — and the student then sees their own attendance percentage, the published mark, the timetable of their class, their attendance and their agreed fee, while a student is still refused the fee management; a teacher sees only the classes of their own assignments |
| AttendanceExportBrowserTest  | 4     | the administrator sees the "Download CSV" link; the downloaded file carries the expected header, the student's record and the report's own numbers, and its name reflects the selected range; the printable sheet shows course, batch, subject, teacher, the roster and the recorded mark, hides its controls in print media and prints (via `page.pdf()` + PDFBox) the heading, the student name and the batch; a teacher is denied every export and another teacher's sheet |
| BatchSubjectLifecycleBrowserTest | 3 | a subject with classes shows **Deactivate subject** and the reason it cannot be removed, and **no** Remove button; an active subject never offers Activate and a deactivated one never offers Deactivate; deactivating succeeds with the success message and keeps the class listed in the sessions; a subject that was never used still offers the real Remove action and removing it succeeds. The screenshot of the repaired page is written to `target/browser-artifacts/batch-subject-lifecycle.png` |
| SubjectLifecycleBrowserTest | 5 | the administrator sees the curated catalogue in the ordinary course and subject screens and a course lists exactly the subjects it teaches; an administrator deactivates and reactivates a subject through the rendered page; a deactivated subject is no longer offered when a subject is assigned to a course; a deactivated subject keeps its existing course assignment, marked as deactivated on the course page |
| InstallmentPlanEditBrowserTest | 3 | the administrator opens the edit form, sees the current schedule loaded into it, corrects the amounts and a due date and sees the proposed total and the difference update live, then saves and the corrected schedule is shown while the net fee is unchanged; a schedule that does not add up is refused by the server and the original one is still there; once a payment has been booked, the *Edit installment plan* action is gone, the page explains why and the direct URL is refused with the same explanation |

Print verification detail (verified in the fees suite): under
`page.emulateMedia({media: "print"})` the `.no-print` controls compute
`display: none`, the receipt renders the clean sheet with its balance
note; `page.pdf()` produces real PDFs (`receipt-printed.pdf`,
`receipt-voided-printed.pdf` in `target/browser-artifacts/`) whose
extracted text contains the receipt number, amount `4000.00`/`5000.00`
and the `VOIDED` stamp for the voided payment. Screenshots of the screen
and print renderings are saved next to the PDFs on every run.

**Combined run: `mvn test -Pbrowser-tests` = 415 tests, 0 failures, 0
errors, 0 skipped** (unit + service + browser in one Maven invoke).

E2E smoke suite (`zsh scripts/e2e-smoke.sh`, final run **208 checks,
0 failed** — the 127 of Phases 1–3 plus 27 new checks that download the
batch, date, student and subject CSVs over real HTTP, verify the
content type, the attachment file name and the header row, open the
printable sheet of a held class and confirm that a teacher is refused
both the exports and a foreign teacher's sheet, plus 23 new checks that
create an installment plan over HTTP (including a refused schedule that
does not add up), verify the allocation of an earlier payment and of a
new one, check the reconciliation, void an allocated payment and
confirm that the teacher is refused every installment URL and the plan
form, plus 31 checks that create a recurring timetable over HTTP
(including a refused double booking and an idempotent class
generation), create an examination, refuse a mark above the maximum,
publish the results as an administrator, refuse the publication for a
teacher, create and link a student portal account, log the student in
and check that they see their own records and are refused the
management screens) — real HTTP flows
against the running app with DB row
assertions through psql. Every Phase 2 flow over HTTP was exercised:
fee agreement create + duplicate/over-discount/closed-enrollment/bad-year
rejections; payment form rendering; recording with receipt format
`OMS-RCP-2026-######` from the sequence; outstanding recalculated after
payment, after void; overpay rejection; fee amounts locked once payments
exist; void-without-reason rejected, void with reason persisted, receipt
shows the VOIDED stamp; receipt numbers strictly increase; session
create → attendance row → cancel blocked → complete → edit blocked;
cancelled session rejects attendance; batch/date/student reports render
with the recorded data; teacher can create sessions and record
attendance for an owned subject, is 403'd on fee/payment/report URLs,
403'd recording another teacher's subject and blocked from correcting a
completed session; administrators reach every Phase 2 page; dashboard
renders the Phase 2 blocks.

Smoke-test artifacts: `/tmp/opencode/e2e-phase2.log`, `test-phase2.log`.

---

## 5. Attendance CSV export and printable attendance sheets

### What was built

**CSV export** of the four existing attendance reports, behind the
administrator-only report pages:

| Report | Endpoint | Content |
|--------|----------|---------|
| Individual student | `/attendance/reports/export/student/{id}?from=&to=` | one line per enrollment period (transfers keep their history) |
| Batch | `/attendance/reports/export/batch?batchId=&from=&to=` | one line per student of the batch |
| Subject-wise | `/attendance/reports/export/subject?batchId=&courseSubjectId=&from=&to=` | one line per student of that subject in that batch |
| Date-wise | `/attendance/reports/export/date?date=` | one line per recorded attendance mark of that date |

- **No duplicated business logic**: `AttendanceExportService` asks
  `AttendanceReportService` for the very same rows the pages render;
  the export service only chooses columns and the file name. The
  percentage rule is untouched: (present + late) ÷ (present + late +
  absent) × 100, excused excluded, cancelled and open sessions never
  in a denominator.
- **Filters carry over**: the download uses the filters currently
  selected on the page; a filtered download contains no record outside
  the selection.
- **Honest values**: an undefined percentage (no evaluated attendance)
  is an **empty cell**, never `0`; `0.0` only appears when the formula
  really yields zero.
- **CSV correctness**: UTF-8 (`text/csv;charset=UTF-8`), a header row,
  RFC 4180 quoting (fields with a comma, a quote or a line break are
  quoted, embedded quotes are doubled), and spreadsheet-formula
  neutralisation: a value starting with `=`, `+`, `-` or `@` is
  prefixed with an apostrophe so it is imported as text. Ordinary
  names, numbers and dates are written unchanged. The file name is
  built from the report type and the selected dates only
  (`attendance-batch-2026-10-01-to-2026-10-31.csv`) — no user-entered
  text reaches a file name. No internal ids, account data or fee
  information is exported.
- A small reusable writer (`common/CsvWriter`) does the escaping; **no
  CSV library was added**.

**Printable attendance sheet** at `/sessions/{id}/print`, opened from
the class session page ("Print attendance sheet"):

- institute heading, course, batch, academic year, subject, class date,
  time, teacher, topic and session status;
- the **eligible roster of that class date** (enrolment period must
  cover the date — a later enrollee or an earlier leaver is not
  listed, and a transferred student keeps the class they were on);
- a box per status (Present, Absent, Late, Excused) and a remarks
  column, with the already recorded status ticked;
- signature and date lines;
- print rules in a served stylesheet
  (`static/css/attendance-sheet.css`, because the CSP forbids inline
  CSS): the controls disappear, backgrounds are dropped, the table
  header repeats on every page and a student row is never cut in half.
  Printing and "save as PDF" remain the browser's job — no PDF
  generation dependency was added.

### Authorization (unchanged model, enforced server-side)

- CSV exports are **administrator-only** (class-level
  `@PreAuthorize("hasRole('ADMIN')")` plus the URL rule): a teacher
  gets the 403 page for every export, verified in the web test, in the
  smoke suite and in the browser suite.
- The printable sheet follows the class-session ownership rules:
  `ClassSessionService.getAuthorized` decides, so an administrator may
  print any session and a teacher only the sessions of their own
  assigned subjects. Another teacher's sheet is 403 — verified the same
  three ways.
- The new endpoints are GET-only, so no state changes and no new CSRF
  surface; the existing CSRF protection of the forms is untouched, and
  no fee, payment or account rule was weakened.

### What was verified

- **29 new JUnit tests** (CSV writer, export endpoints, printable
  sheet) — all pass; see §4 for the breakdown.
- **4 new browser tests** (`AttendanceExportBrowserTest`) — the
  download link, the downloaded file's header, record, numbers and file
  name, the filter effect, the printable sheet on screen, in print
  media and as a PDF parsed with PDFBox, and the teacher walls.
- **27 new smoke checks** in `scripts/e2e-smoke.sh` — the exports
  downloaded over real HTTP against the running application, the
  content type, the attachment name, the header row, the sheet and the
  teacher walls.

## 6. Installment plans and payment allocation

### What was built

**Installment plans** let an administrator divide the agreed net fee of
one fee agreement into scheduled amounts with due dates. The financial
model was not changed:

- the **fee agreement** stays the authoritative record of what is owed —
  a plan schedules it, it never adds liability and never changes the
  gross fee, the discount or the net fee;
- the **payment** stays the authoritative record of money received — an
  allocation never changes a payment's amount, date or receipt number;
- no second ledger was introduced: the allocations are a link between
  payments and installments, and every figure is derived from the
  existing payment rows.

**New tables** (migration `V3__installment_plans.sql`, applied to
`omsai` on 2026-10-21):

| Table | Key constraints |
|-------|-----------------|
| `fee_installments` | `UNIQUE (fee_account_id, installment_number)`, `amount NUMERIC(12,2) CHECK (amount > 0)`, `installment_number CHECK (> 0)`, `ON DELETE RESTRICT` to the account and the creator, indexes on `(fee_account_id, installment_number)` and `due_date` |
| `payment_allocations` | `UNIQUE (payment_id, installment_id)`, `amount NUMERIC(12,2) CHECK (amount > 0)`, composite foreign keys `(payment_id, fee_account_id)` and `(installment_id, fee_account_id)` that prove in the database that payment and installment belong to the **same** fee account, `ON DELETE RESTRICT` |

The composite keys required two additional unique constraints
(`payments (id, fee_account_id)`, `fee_installments (id, fee_account_id)`)
— additions to existing tables, no existing table or row was dropped or
rewritten. The browser and unit test databases received the same
migration automatically (Flyway runs at startup).

**Plan creation** (`/fees/{id}/installments`, administrator only) —
server-side rules, all tested:

1. the fee agreement must exist and must not have a plan yet (a repeated
   submission is refused, so no duplicate plan can be created);
2. at least **two** installments;
3. every amount greater than zero, at most two decimal places, at most
   ten integer digits;
4. every due date present and valid;
5. the installment numbers must be **1..n without gaps**;
6. the amounts must add up to the **net fee exactly** — a mismatch is
   refused with the exact numbers, the amounts are never adjusted
   silently;
7. an invalid plan writes nothing at all (the whole operation is one
   transaction).

The form shows the agreement it divides and a table of rows; a small
vanilla script (`/js/installments.js`, CSP-safe — no inline script) adds
and removes rows and shows the running total as a convenience only.

**The allocation policy**: a new payment is allocated to the
earliest-due installment that still has a balance, moving on to the next
one as needed; the installment number breaks ties between equal due
dates. A payment may cover part of one installment or parts of several.
The payment, its receipt and its allocations are written in **one
transaction** — if the payment cannot be allocated completely, the
payment is rolled back with it. An agreement without a plan keeps the
Phase 2 behaviour exactly.

**Payments received before the plan**: the existing RECEIVED payments of
the agreement are allocated to the new schedule in a deterministic order
(payment date, then creation time, then id), filling the earliest due
date first. Voided payments are ignored, no payment is created or
changed, and the operation rolls back completely if the totals do not
reconcile.

**Voiding**: the existing voiding workflow is untouched — the payment
stays, keeps its receipt number and records who, when and why. Its
allocations are **kept** (audit history) but stop counting, because
only allocations of RECEIVED payments are effective. The installment
balances and their statuses are derived again, so the installment
returns to its full outstanding amount and the next payment is
allocated to the earliest installment that has a balance again.

**Status** is derived, never stored: `PAID` (nothing remains), then
`OVERDUE` (due date passed, balance remains — also for a partly paid
installment), then `PARTIALLY_PAID`, then `DUE_TODAY`, then
`UPCOMING`. Every screen shows the scheduled amount, the amount paid
and the remaining balance next to it.

**Reconciliation**: while a plan exists, the sum of the installment
balances equals the agreement's outstanding balance. This is verified by
a test that compares the database aggregates of both sides.

### Screens

- the fee agreement page: the schedule with derived balances and
  statuses, the summary (number of installments, overdue count,
  scheduled/paid/remaining, next due date) and the *Create installment
  plan* action while no plan exists;
- `/installments` — the collection overview: total outstanding of all
  agreements, the part covered by a plan, the overdue balance and count,
  the amount due within seven days and the next installments due;
- `/installments/upcoming` — installments with a balance inside a
  selected due-date range, filtered by student, course and academic
  year, paginated;
- `/installments/overdue` — past-due installments with the number of
  days late;
- the dashboard: installments due within seven days, the overdue
  installment balance and the number of overdue installments.

The installment balances are the same money as the agreements'
outstanding balance, split by due date; the overview never adds them
together and says so on the page.

### Authorization

Every installment screen and action is administrator-only: the URL rule
of `SecurityConfig`, the class-level `@PreAuthorize("hasRole('ADMIN')")`
of the controllers and the service-layer checks. Teachers are refused
with 403 — by URL, by form submission and by guessed ids — and never see
the navigation entry. The plan form is a POST and therefore CSRF
protected like every other form. No fee, payment, receipt or account
rule was weakened, and the plan creation needs no active enrollment: an
existing, legitimate fee obligation can be scheduled whatever the
enrollment status is.

### Schedule modification policy (deliberate limitation)

A plan is created once and then read-only. The amounts and the numbering
of a schedule are never rewritten silently and installments are never
deleted. Changing a schedule safely needs an adjustment/rescheduling
model (which installment absorbs the difference, how a partly paid
installment is re-cut, how the receipt history is kept coherent) that is
**not** part of this phase — the limitation is documented rather than
worked around unsafely.

## 7. The final completion release — timetable, examinations, portals

### Timetable

A **timetable entry** is a planned recurring class: one batch-subject
assignment, one weekday, a time range, an optional classroom, an
optional topic and an effective period. The batch, the course, the
subject and the teacher are derived from the assignment, so a schedule
can never disagree with the teaching assignments.

**Conflict detection runs on the server.** Two schedules conflict when
they are on the same weekday, their time ranges overlap
(`startA < endB AND startB < endA` — a class ending exactly when the
next one begins is fine) and their effective periods overlap. The
conflict may be the **teacher** (assigned to two classes at once), the
**batch** (two classes of the same batch) or the **classroom** (double
booked); the message says which one. A deactivated entry frees its slot
again.

**Calendar exceptions** are separate from the schedule: a holiday
closes the institute for a day, a cancelled occurrence cancels one
class and a rescheduling moves it to another date. An exception never
deletes the recurring entry, and it can be removed again.

**Generating the classes** turns the occurrences of a date range into
ordinary class sessions, which then behave exactly like a manually
created class (attendance, reports, receipts). The run is idempotent:
a class that already exists for the assignment and the date is never
touched or duplicated, holidays and cancelled occurrences are skipped,
and completed classes keep their attendance untouched. A session is
created through the existing `ClassSessionService`, so the established
rules (active batch and course, valid times) apply.

### Examinations and results

An **examination** belongs to one class, has a maximum mark and follows
`DRAFT` → `PUBLISHED` (plus `CANCELLED`, which keeps its results). A
**result** belongs to the student's *enrollment*, which is what keeps
the academic history when a student transfers to another batch.

The roster of an examination is the class roster **on the examination
date**: a student who enrolled later, or whose enrollment had already
ended, cannot have a result. Marks are validated (0 … maximum, at most
two decimals); an absent or excused student has **no mark** — that is
not a mark of zero. One result per examination and enrollment (a second
entry corrects the first). Only an administrator publishes; a published
result is corrected with an audit trail, never deleted.

Reports: the academic history of a student, the results of a batch
(present/absent/excused, average and highest mark — the average counts
only the students who sat the examination) and the published results a
student sees.

### The three portals

| Role | Home | Content |
|------|------|---------|
| ADMIN | `/dashboard` | the management portal: live figures, today's classes, the installment figures and a link to every module |
| TEACHER | `/teacher` | today's classes, the attendance still to record, the examinations of their own classes, the recent classes, their own schedule and their teaching assignments |
| STUDENT | `/student` | their own dashboard, timetable, attendance, fees and installments, published results and their profile |

A **student account** is created by an administrator and linked to
exactly one student profile; the unique index on
`students.user_account_id` keeps the link one-to-one in the database
(a profile cannot be linked to two accounts and an account to two
profiles). The portal derives everything from the signed-in account —
there is no student id in the URL — so a student can never reach another
student's records by editing the address. Students may edit their
contact details and change their own password; they can neither change
their student code, nor attendance, fees, payments or marks.

### Authorization (tested, not just hidden)

- Administrators reach the management and teacher screens.
- Teachers reach the timetable and the examinations of their own
  classes, and are refused every financial route (`/fees`, `/payments`,
  `/installments*`).
- Students reach only their own portal and are refused the management
  screens, the reports of other students and every state-changing form.
- Unauthenticated visitors are redirected to the login page.

The role isolation is covered by 13 web tests, the assignment scoping
by the service tests and by the browser workflow, and the URL rules,
the class-level `@PreAuthorize` and the service checks are all in place.

### Production readiness

- A **`prod` profile** (`application-prod.yml`) with no built-in
  credentials: the database URL, user and password must come from the
  environment, a missing variable stops the application from starting.
  The production profile also raises the BCrypt cost to 12, disables
  the bootstrap admin by default and marks the session cookie
  `secure`.
- **Backup and restore scripts** (`scripts/backup-db.sh`,
  `scripts/restore-db.sh`). The restore refuses the live development
  and test databases, needs an explicit target and an explicit
  confirmation, and can be run as a dry run first. **The procedure was
  actually executed** in an isolated environment during this release:
  a database was backed up, the dry run changed nothing, the restore
  recreated the target with the original rows, and the rows were
  verified; the probe databases were dropped afterwards.
- **Deployment documentation** (README §14): requirements, build and
  start, the environment variables, the reverse-proxy and cookie notes,
  the backup and restore procedure, the shutdown behaviour and the
  verification steps.

## 8. Defects found during Phase 2 — and fixed

All of the following are real bugs discovered by the smoke suite (the
MockMvc-in-a-transaction tests masked several of them; running the app
for real exposed them). All are fixed.

1. **`LazyInitializationException` in `SessionAuthority.currentUser()`**
   — the method returned a Hibernate *proxy* via `getReferenceById`, and
   page controllers read the role outside any transaction → 500 on
   `/sessions` (for both roles). Fixed by loading the real entity with
   `findById`. The same treatment was applied to the `admin()` helpers
   of both fee controllers for consistency.
2. **Model attribute named `session` collided with Thymeleaf's web
   implicit variables** — in a real servlet request the HTTP session
   (present after login) shadows the model attribute, so
   `${session.batchSubject…}` evaluated against the session → 500 on
   every session detail/attendance/form page after login. Fixed by
   renaming the model attribute to `cs` in `SessionController` and every
   affected template expression (including bare `session.` forms).
3. **Native report queries failed with non-null date filters** —
   `(:from is null or cs.session_date >= :from)` defeated Postgres
   parameter typing ("could not determine data type of parameter $2")
   during prepare. Fixed by `cast(:from as date)` in the six affected
   native queries; HQL searches keep Hibernate's typed binding and are
   unaffected.
4. **Batch picker crashed on the report pages** — `course.name` on lazy
   detached batches → 500. Fixed with a fetch-joined
   `findAllForSelection()` used by both report pages.
5. **Teacher's assignment dropdown crashed the session form** —
   `findForUser` did not fetch the teacher; rendering `a.teacher…` on a
   detached proxy → 500. Fixed with `left join fetch bs.teacher` (and
   the batch's course fetch, which its active-course filter needed).
6. **Flash error messages were overwritten by the attendance page** —
   `attendancePage` unconditionally wrote its own `errorMessage`, which
   clobbers the flash message the service redirect had queued (flash
   attributes merge before the handler runs), so a rejection like
   "Attendance cannot be recorded for a cancelled session." was hidden
   behind a generic notice. Removed the redundant model write; the page
   keeps its inline cancelled-session warning.
7. **Missing request parameter produced a 500** — `GET
   /payments/new` requires `feeAccountId`; opening the URL bare returned
   an unhandled `MissingServletRequestParameterException`. Added a
   dedicated handler returning **400** with a friendly page.
8. **Two template defects**: the payment form's nested enum reference
   `T(com.omsai.coaching.fees.Payment).Method.values()` failed in SpEL
   (needs `Payment$Method`, the Java inner-class syntax); and a
   DashboardServiceTest fixture violated the enrollment constraint added
   in Phase 2 (§2).

---

## 9. Phase 1 defects (for completeness)

1. Rate limiter off-by-one (fixed in Phase 1).
2. Teacher `/` redirect could not work (fixed in Phase 1).
3. Teacher detail page crash on `linkedUser` proxy (fixed in Phase 1).
4. Batch detail page crash on `course` proxy (fixed in Phase 1).
5. Invisible `endDateAfterStart` validation error (fixed in Phase 1).
6. Thymeleaf 3.1.5's restricted `new`-word fragment parsing (worked
   around in Phase 1).

---

## 10. Phase 3 defects — real-browser bugs found and fixed

The smoke suite verifies HTTP semantics; only a real browser caught the
following rendering defects. All are fixed, and all four browser test
classes then pass.

1. **Bootstrap Icons font 404** — `bootstrap-icons.min.css` referenced
   its `fonts/` sibling relative to the CSS file's own directory, but
   the project serves CSS under `/css/` and fonts under `/fonts/`, so
   every page loaded `/css/fonts/bootstrap-icons.woff2` → 404 (icons
   render as empty squares). Fixed the path to `../fonts/`; the font
   now serves 200 and sidebar/topbar icons render.
2. **Content-Security-Policy blocked all inline styles** — the app CSP
   (`style-src 'self'`) correctly forbids inline styles, but five
   templates used them: a void-popover positioner, two input widths and
   a column width. The violations were caught by the suite's console
   monitor as real CSP console errors. Fixed by moving each rule into
   `static/css/app.css` (new classes `.void-popover`,
   `.w-max-9rem`, `.w-min-14rem`, `.col-min-10rem`) and referencing the
   classes from the templates.
3. **Receipt print CSS never actually applied** — the receipt's entire
   print stylesheet lived in an inline `<style>` block, which the same
   CSP blocks, so in a real browser the print/PDF output kept the
   on-screen buttons and background (the browser test failed with
   `display: flex` where `none` was required). Fixed by moving the rules
   into a served stylesheet `static/css/receipt.css`, linked from the
   template. Print emulation and the PDFs now show the clean receipt,
   and this is asserted in the tests (screen + print + PDF text).
4. **Teacher sidebar leaked administrator-only links** — the layout put
   `th:if="${isAdmin}"` and `th:replace` on the same element; Thymeleaf
   processes `th:replace` (inclusion, precedence 100) *before* `th:if`
   (precedence 400), so the guard never applied and teachers saw
   "Attendance reports / Fees / Payments / Accounts" links that then
   403'd. The server-side authorization was fine — this is a UI leak.
   Fixed by wrapping the four admin-only items in a conditionally
   rendered `<div>`; the browser test asserts a teacher's sidebar no
   longer offers them (and the 403s on direct navigation remain).

Also verified (and tolerated as harmless): Chromium logs a console
*warning* "The specified value \"20/10/26\" does not conform to the
required format, \"yyyy-MM-dd\"" when a date input is restored by the
browser's own form restoration on `/sessions/new`; it is a browser-
internal message (type warning, not error), the submitted value is
verified ISO (`2026-10-20`), the session is created with the correct
class date and the report counts match. The failure monitors count only
console *errors*, and this warning does not affect any behaviour.

---

## 11. Environment & honest limitations

- PostgreSQL 18.4 local cluster on port **5433** (databases `omsai` and
  `omsai_test`; database credentials are supplied through environment
  variables — the repositories and the scripts store no values),
  Java **25**, Maven 3.9.16 (wrapper included). Local-development
  defaults only; change via environment variables.
- **No container test story** — Docker unavailable; tests need the three
  real disposable PostgreSQL databases documented in README §1/§7.
- **Browser verification is real but local** — Playwright 1.63.0 drives
  the bundled headless Chromium (build 1243; a system Chromium works too
  via `PW_BROWSER_CHANNEL`/`PW_BROWSER_EXECUTABLE`). No cross-browser
  matrix (WebKit/Firefox) was run: the application is plain
  server-rendered Bootstrap, but claims are limited to Chromium.
- **Rate limiter is single-node/in-memory** (Phase 1 limit; unchanged).
- **Receipt printing is browser print** — no PDF engine; the printed
  sheet is verified at the browser level (print-media emulation +
  Chromium's own `page.pdf()` pipeline + PDFBox text extraction), but
  the *native* print dialog and physical paper output of a real printer
  driver cannot be exercised by automated tests — correctness there is
  the same code path (`@media print` CSS), which is verified.
- **CSV exports are UTF-8 without a byte-order mark.** Modern Excel,
  LibreOffice, Google Sheets and Numbers read them correctly; a legacy
  Windows Excel with a non-ASCII student name may show mojibake unless
  the file is imported with the UTF-8 codepage. A BOM was deliberately
  not added, because it would end up inside the first header cell for
  every programmatic reader.
- **Formula neutralisation is visible in a raw text editor**: a value
  that starts with `=`, `+`, `-` or `@` is prefixed with an apostrophe
  so a spreadsheet imports it as text. The apostrophe is not shown by
  spreadsheet applications, but it is part of the downloaded bytes.
- **The date-wise export lists recorded marks only** — a session of that
  date without recorded attendance contributes no line. Nothing is
  invented for it, and the file stays one line per student mark.
- **The printable sheet is verified through Chromium's print pipeline**
  (print-media emulation plus `page.pdf()`), not through a physical
  printer driver; the `@media print` rules that decide the paper
  layout are the ones under test.
- **Money is `NUMERIC(12,2)` and BigDecimal end-to-end**; display
  formatting is the plain BigDecimal string (no currency formatter).
  This is intentional for honest arithmetic (no rounding surprises), but
  a locale-aware formatter would be nicer eventually.
- **Installment plans are read-only after creation.** A schedule cannot
  be edited, rescheduled or deleted, and no installment can be removed:
  the amounts and the numbering of a schedule are never rewritten
  silently. Doing that safely needs an adjustment/rescheduling model
  (which installment absorbs a difference, how a partly paid installment
  is re-cut, how the receipt history stays coherent) that is **not** part
  of this phase — the limitation is documented instead of an unsafe
  workaround. A plan can still be created for an agreement that already
  received money.
- **The overview shows two overlapping figures on purpose**: the total
  outstanding of all agreements and the part of it that is covered by an
  installment plan. They are the same money seen at two levels, so the
  page says so and never adds them.
- **No production hardening checklist yet** (TLS/proxy, secure session
  cookie behind HTTPS, log aggregation, backups) — operator tasks.
- **The student portal is deliberately narrow**: it shows the student's
  own timetable, attendance, fees, installments, published results and
  profile — and nothing else. A parent portal was not built (no
  verified business requirement).
- **The timetable has no per-occurrence room change**: a classroom is
  a property of the recurring entry, not of a single occurrence. A
  one-off change is modelled as a cancellation plus a new entry.
- **The backup/restore procedure was verified locally, not in a
  production environment**: a backup, a refused dry run, a restore into
  an isolated database and a data check were executed during this
  release, but no production backup cycle has run yet.
- **CI is configured but not executed** — `.github/workflows/ci.yml`
  (JDK 25 + PostgreSQL 16 services, Playwright Chromium install,
  `mvn test` and `mvn test -Pbrowser-tests`, artifacts on failure) is
  committed, but this machine has no GitHub remote, so no real Actions
  run exists to cite. The workflow mirrors the locally verified
  commands.
- **Test-created committed fixtures in the concurrency test** are the
  sanctioned exception to "never delete": `PaymentServiceConcurrencyTest`
  commits real data (to race threads), then deletes it in `@AfterEach`
  in FK order. The browser tests follow the same principle: they delete
  *only the rows the run itself created* (tracked IDs, FK order), never
  the bootstrap admin or pre-existing data; if a run dies mid-flight,
  `zsh scripts/reset-browser-db.sh` rebuilds the disposable
  `omsai_browser` from scratch.
- **Browser-test database isolation is enforced, not merely documented**:
  `BrowserTestBase` queries `current_database()` on the live connection
  before any test runs and aborts unless the name starts with
  `omsai_browser`. Verified negatively by pointing the suite at a
  scratch database — the run stops with *"Browser tests refuse to run
  against database …"* before a single test executes, so
  `omsai`/`omsai_test` cannot be modified even by a misconfigured
  `BROWSER_TEST_DB_URL`.

## 12. Suggested next steps (Phase 6+)

1. Run the **backup cycle in the target environment** and schedule the
   first restore test — the procedure is verified locally, the
   operation is not.
2. Execute the **CI workflow** against a real remote and keep it green
   on every push.
3. An **installment schedule adjustment model** — the documented
   limitation of this phase: a safe way to re-cut a schedule that
   already carries payments (which installment absorbs the difference,
   how the audit trail stays coherent).
4. **Automatic overdue reminders** to the parents/teachers (e-mail or
   WhatsApp) on top of the overdue report — deliberately not built
   here.
5. Fine-grained per-teacher report access (if teachers ever need their
   own subject's report), with the same service-level enforcement.
6. Optionally add a WebKit/Firefox cross-browser matrix to the
   Playwright suite.
7. Docker one-command environment (compose + Testcontainers).
8. Attendance CSV **import** (upload a marked sheet back into the
   session) — the export side of the same idea is done.
9. An optional "CSV with BOM" export variant for legacy Excel users.
10. A **payment plan for a course** (a course-wide default schedule
    that new agreements inherit) instead of scheduling every agreement
    by hand.

*(The earlier suggestions "attendance export (CSV) and a printable batch
attendance sheet" and "installment plans / payment schedules with
automated overdue flags" are complete — they were the work of the
previous two rounds.)*

*(The former "browser-rendering verification" suggestion from Phase 2 is
complete — that was Phase 3.)*

---

## 13. Client readiness audit (2026-10-21) — full report in `CLIENT_READINESS_AUDIT.md`

A systematic audit of the whole application was carried out against the
working tree, followed by **one** narrowly scoped repair. The full
report — repository state, mock-data inventory, the complete module and
action matrix, the P0–P3 backlog and the test evidence — is in
[`CLIENT_READINESS_AUDIT.md`](CLIENT_READINESS_AUDIT.md). This section
records only the milestone and the repair.

### 13.1 What the audit established

- **The working tree was not clean.** A large body of uncommitted work
  (timetable, examinations, installments, student portal, production
  profile, migrations V3–V6, backup scripts) existed at HEAD `b27b1ca`.
  All of it was preserved; a byte-level baseline was captured before
  any edit and the new work is strictly additive.
- **The production application creates no demonstration data.** No
  `data.sql`, no seed runner, and all six existing migrations are
  schema-only. The single `CommandLineRunner`
  (`AdminBootstrapRunner`) is opt-in, environment-driven, refuses a
  password under 12 characters and is idempotent.
- **No fabricated operational figures.** Every dashboard and detail
  number comes from a live database query; the `₹ …` strings in the
  templates are Thymeleaf prototype text that is always replaced.
- **The production profile has no built-in credentials** and refuses to
  start without them. The only weak spot is the *development*
  administrator, which still uses the password published in
  `scripts/e2e-smoke.sh` — recorded as P3-5.
- **No P0 finding.** Authorization, payment immutability, foreign-key
  protection of history and the production secret handling were all
  verified to be sound.

### 13.2 Root cause of "a class can be created, but removal is unclear"

The reported symptom was reproduced against the running application:

```
POST /batches/subjects/{id}/remove   →   HTTP 409
"Something went wrong — That change conflicts with existing data -
 a record with the same details may already exist."
```

The message was wrong (the cause was a dependency, not a duplicate),
the user was left with no path forward, and the confirm dialogue
claimed the removal would succeed.

**Four candidate entities were audited separately:**

| Entity | Lifecycle before the repair | Verdict |
|---|---|---|
| `ClassSession` (one-off class) | `OPEN → COMPLETED / CANCELLED`, edit while OPEN, cancel refused when attendance exists — with a precise message | already complete |
| `TimetableEntry` (recurring schedule) | create / edit / **deactivate** | already complete |
| `Batch` | create / edit / **activate & deactivate** | already complete |
| **`BatchSubject`** (one subject of one course, taught in one batch, by one teacher — the entity that actually *is* a class) | **no lifecycle action at all; a single unguarded `deleteById()`** | **the culprit** |

`BatchService.removeSubject()` deleted the row without checking the
three `ON DELETE RESTRICT` foreign keys that protect the history
(`class_sessions`, `timetable_entries`, `examinations`), so the delete
always failed once the subject had been taught. The **sibling method**
`CourseService.removeSubject()` already had exactly the right guard —
the intended pattern existed one level up and had simply never been
applied here. There was no test for it.

### 13.3 The repair — batch-subject class lifecycle

`batch_subjects` gained an `active` flag (Flyway **V7**, additive and
safe: existing rows stay active), bringing it in line with `Course`,
`Batch` and `TimetableEntry`, which all already deactivate.

- **Hard delete is now only possible for an assignment that has never
  been used.** When classes, a timetable entry or examinations depend
  on it, the removal is refused with an actionable message naming the
  dependency, instead of a database error:
  *"This subject cannot be removed from the batch because 2 class
  sessions depend on it. That teaching history must be preserved.
  Deactivate the subject instead to stop offering it for new classes."*
- **A new deactivate / reactivate action**
  (`POST /batches/subjects/{id}/status`) keeps the assignment and all
  of its history while taking it out of every picker, so no new class
  session, timetable entry or examination can be created for it.
  Reactivating restores it with its teacher and history intact.
- **The page no longer offers a misleading button.** A subject that was
  never used shows *Remove subject*; a subject with history shows
  *Deactivate subject* together with the reason it cannot be deleted.
- **Re-adding a deactivated subject reactivates it** instead of
  colliding with the unique constraint.
- The batch's own button and the subject's button are now labelled
  distinctly (*Deactivate batch* vs. *Deactivate subject*), because
  two different "Deactivate" actions shared the page.

**Nothing was deleted.** No class session, attendance row, payment,
enrollment or examination was touched, no foreign key was relaxed, and
no existing record changed meaning.

### 13.4 Verification of the repair

| Layer | Tests | Result |
|---|---|---|
| Service | 16 (`BatchSubjectLifecycleTest`) | all pass |
| HTTP + authorization | 9 (`BatchSubjectAccessWebTest`) | all pass |
| Real Chromium | 3 (`BatchSubjectLifecycleBrowserTest`) | all pass |
| **Whole suite** | **304** (`mvn test -Pbrowser-tests`) | **0 failures, 0 errors** |

Verified live against the running application after Flyway applied V7
to the `omsai` database, and visually via a real-Chromium screenshot
(`target/browser-artifacts/batch-subject-lifecycle.png`).

Two bugs in the repair itself were caught during validation and the
tests were **tightened** rather than the assertions relaxed: a
condition that rendered *Deactivate* and *Activate* at the same time
(found by a live HTTP check), and an inline `style` attribute that
violated the application's `style-src 'self'` CSP (found by the browser
suite).

### 13.5 What the audit did **not** fix

Only the class lifecycle was repaired. The following were **audited and
reported, not changed** — see the backlog in `CLIENT_READINESS_AUDIT.md` §12:

- **P1-2** an installment plan can be created but never edited or replaced;
- **P1-3** a standalone subject has no delete and no deactivate action;
- **P2-1** a subject's name inside a course cannot be edited;
- **P2-2** a single timetable occurrence cannot be rescheduled;
- **P2-3** published results have no distinct, auditable amendment workflow;
- **P2-4 / P2-5** list-page search and a payment-allocation reconciliation view;
- **P3-2** CSV downloads log a false `ERROR` (broken pipe) when the client disconnects early — pre-existing, found in the baseline run;
- **P3-5** the development administrator still uses the password published in the smoke-test script.

The E2E-prefixed records in the `omsai` development database were
**identified and left untouched**, together with two genuine
demonstration students (`Aarav Sharma`, `Dia Patel`) and their course,
batches and subjects, and a set of debug artifacts whose provenance
cannot be established. A cleanup must be reviewed separately, must show
exactly which records it would affect, and must never run automatically
against an unknown database.

---

## 14. Client readiness repair 2 (2026-10-21) — P1-2: editing an installment plan

The second repair of the client readiness audit. The full detail is in
[`CLIENT_READINESS_AUDIT.md`](CLIENT_READINESS_AUDIT.md) §15; this
section records the milestone. **Only the installment-plan editing
workflow was touched — no other audit finding was started.**

### 14.1 The gap

`InstallmentService` had a single entry point for a schedule, and it was
**create-only by construction**:

```java
if (installmentRepository.existsByFeeAccountId(feeAccountId)) {
    throw new BusinessRuleException("This fee agreement already has an installment plan.");
}
```

The controller mirrored it — `GET /fees/{id}/installments` bounced back
to the fee account page as soon as a plan existed. An institute that
typed a wrong fee schedule therefore had **no way out**: no edit, no
delete-and-recreate, nothing.

The database never objected (the rows are only restricted against
`payment_allocations`), so the blocker was purely in the service layer
— and it was over-broad. The real reason a schedule must not be
replaced is *"a payment has been booked against it"*, not *"it is a
schedule"*.

### 14.2 The rule now implemented

| Situation | Allowed |
|---|---|
| The plan has **no** payment-allocation history | The administrator may change amounts, change due dates, add rows, remove rows and correct the numbering. The new schedule must still add up to the net fee exactly. |
| The plan **has** payment-allocation history | The replacement is refused — **including when the payment was voided afterwards**, because the allocations of a voided payment are kept as audit history. |

The refusal message:

> This installment schedule cannot be edited because payment-allocation
> history exists. The original schedule and financial history have been
> preserved.

The rule lives in the **service layer** (`InstallmentService.replacePlan`),
not in a hidden button. The button is hidden as well, and the fee
account page explains why.

### 14.3 How the replacement works

* The rows that keep their installment number are **updated in place** —
  their identity, their creator and their creation date survive; the
  surplus rows are removed and the missing ones are inserted. An
  installment number is never moved, so the unique key
  `(fee_account_id, installment_number)` cannot be violated.
* The fee agreement is never touched: gross fee, discount and net fee
  stay read-only in this workflow.
* All the creation rules are reused (`InstallmentService.parse(...)`
  and the total check), so the editing rules cannot drift away from the
  creation rules: at least two installments, positive amounts, at most
  two decimals, sequential numbering from 1 to N, a due date on every
  row, and a sum that equals the authoritative net fee exactly. The
  server never adjusts an amount to make the schedule balance.
* The whole operation is one transaction: an invalid submission leaves
  the existing schedule completely unchanged.
* Money is `BigDecimal` end to end. The browser preview was moved from
  floating point to **whole cents**, so it can never disagree with the
  server by a rounding error — the server stays authoritative anyway.

### 14.4 Concurrency

The replacement takes **the same pessimistic lock on the fee-account row**
(`FeeAccountRepository.getForUpdate`) that the payment recording
already uses, so a payment and an edit on the same account serialize:

```
PaymentService.record()        ─┐
InstallmentService.createPlan() ─┼─► SELECT … FOR UPDATE on fee_accounts
InstallmentService.replacePlan()─┘
```

Either the payment commits first (the allocation now exists and the
edit is refused inside the lock) or the edit commits first (the payment
is then allocated to the new rows). Money can never end up allocated to
a row that no longer exists.

### 14.5 New files

| File | Purpose |
|---|---|
| `src/main/java/com/omsai/coaching/fees/ImmutablePlanException.java` | lets the controller tell an immutable schedule apart from a fixable submission |
| `src/test/java/com/omsai/coaching/fees/InstallmentPlanEditTest.java` | 24 service tests |
| `src/test/java/com/omsai/coaching/web/InstallmentPlanEditAccessWebTest.java` | 13 HTTP and authorization tests |
| `src/test/java/com/omsai/coaching/browser/InstallmentPlanEditBrowserTest.java` | 3 real-Chromium tests |

**No migration was needed and no existing migration was touched** — the
schema already allowed a schedule to be replaced.

### 14.6 Tests actually executed

| Suite | Before | After | Result |
|---|---|---|---|
| `./mvnw -o test` | 277 | **315** | 0 failures, 0 errors |
| `./mvnw -o test -Pbrowser-tests` | 304 | **345** | 0 failures, 0 errors |
| `zsh scripts/e2e-smoke.sh` | 208 checks | **208 checks** | 0 failed |
| `git diff --check` | clean | clean | — |

The new tests cover: replacing a plan, correcting amounts, changing due
dates, adding and removing rows, sequential numbering, the exact-total
rule, the rejection of every invalid shape (too few rows, non-positive
amounts, three decimals, a missing due date, gaps, duplicates), the
refusal when allocations exist, the refusal when the payment was voided,
the unchanged rows and the unchanged payments/receipts/allocations
after a refusal, the administrator's form round trip, the teacher and
student walls (403 on the URL *and* on a forged POST), the CSRF
protection, the concurrent payment/edit race, and the browser journey
through the real interface.

### 14.7 Data integrity

* `omsai` — 0 orphan installments, 0 orphan allocations; the installments
  (34), allocations (51), payments (80) and fee agreements (31) were not
  modified or deleted by this repair.
* `omsai_test` — no residue after the whole suite (0 installments, 0
  allocations, 0 fee accounts).
* All 17 fee agreements of the development database that carry a plan
  also carry allocations, so they are all immutable — verified live over
  HTTP: the edit action is absent, the explanation is shown and the
  direct URL redirects with the message.

### 14.8 Known limitation (deliberate)

A schedule that has money booked against it can never be corrected — not
even after the payment was voided. Correcting it would need
installment rescheduling and financial adjustments, which are explicitly
out of scope for this task.

A **pre-existing** defect was found but deliberately **not** fixed: a
*missing* fee account raises a `BusinessRuleException` that no global
handler catches, so **every** fee page answers a guessed id with a 500
error page. The new edit endpoints answer it cleanly, but the other
pages were left alone — a global handler would change the behaviour of
every module. It is recorded as **P2-6** in the audit backlog.

---

## 15. Client readiness repair 3 (2026-10-21) — P1-3: subject lifecycle + the curated demo dataset

The third repair of the client readiness audit. The detail is in
[`CLIENT_READINESS_AUDIT.md`](CLIENT_READINESS_AUDIT.md) §16. **Only the
subject lifecycle and the demonstration dataset were touched — no other
audit finding was started.**

### 15.1 The gap

`SubjectService` could create, rename and describe a subject and
nothing else. There was no way to take an obsolete subject out of the
offer and no way at all to remove one that was never used: the only
obstacle was the `ON DELETE RESTRICT` foreign key of `course_subjects`,
and **no service ever explained it**. The block was over-broad in the
same way the batch-subject block was (§10): the real reason a subject
must not be deleted is *"a course already includes it"*.

### 15.2 The rule now implemented

| Situation | Allowed |
|---|---|
| No course includes the subject | **Deleted** (nothing depends on it); it may also be deactivated like any other subject. |
| A course already includes it | **Not deleted** — **deactivated** instead: every course relationship, batch, class and result stays exactly where it is, and the subject leaves the pickers so it cannot be assigned to a new course. |
| Reactivation | A deactivated subject is activated again; no relationship is recreated and nothing is duplicated. |

The rule lives in the **service layer** (`SubjectService.delete` /
`setActive`), not in a hidden button. The subject page shows the
status badge, offers the delete action only for an unused subject and
explains why a used subject cannot be deleted.

`SubjectRepository.getForUpdate` takes a pessimistic lock on the
subject row, which also blocks the foreign-key check of a concurrent
`INSERT` into `course_subjects` — so the "is it still unused?" check
cannot become stale. The `ON DELETE RESTRICT` foreign key remains the
final guard.

### 15.3 The curated demo dataset

* **18 subjects** — reused, never duplicated (Mathematics, Mathematics
  Part I — Algebra, Mathematics Part II — Geometry, General Science,
  Science and Technology, Physics, Chemistry, Biology, English, Marathi,
  Hindi, History and Civics, History and Political Science, Geography,
  Computer Studies, Information Technology, Engineering Mathematics,
  Applied Mathematics).
* **31 course offerings** in seven categories: School Tuition (8),
  Senior Secondary (7), MHT-CET Preparation (5), JEE Preparation (2),
  NEET Preparation (2), Foundation Program (2) and Engineering
  Mathematics (5).
* **9 teachers** and **18 students** with plausible Maharashtrian
  names — **fictional demonstration identities**, not customers and not
  staff.
* **No financial or academic history**: no fee agreement, payment,
  receipt, installment, class session, attendance mark or examination
  result is created, so the dashboard does not look busy because money
  and marks were invented.

Deliberate decisions: Botany and Zoology are **not** separate subjects
(the model has no topic level — Biology carries both parts in its
description); the foundation courses carry the category *Foundation
Program* and say they are an offering of the institute, not an
examination category; every engineering course says its syllabus
follows the affiliated university; and no price, fee, teacher
qualification or examination guarantee appears anywhere.

### 15.4 The demo identities and their safety

| Protection | How |
|---|---|
| No real phone number | Indian mobile numbers start with 6–9; every demo record uses a number starting with `00`, which cannot be a mobile line. |
| No real mail address | Every teacher address uses the reserved domain `example.invalid` (RFC 2606) and can never resolve. |
| No sign-in accounts | The demo teachers and students have **no** login account, so no password is created and none is published. |
| The administrator is never touched | No demo administrator is created. |
| The existing demonstration students | *Aarav Sharma* and *Dia Patel* are **not** in the catalogue and were not duplicated, overwritten or reused. |

### 15.5 The seeder

`DemoDataSeeder` writes the catalogue and nothing else. **Three
independent guards** must agree before a row is written:

1. `app.demo.enabled` — false by default, and the **production profile
   pins it to a literal `false`**, so `DEMO_DATA_ENABLED=true` in the
   environment has no effect on the `prod` profile (verified live);
2. the `prod` profile must not be active — the seeder refuses whatever
   the flag says;
3. `app.demo.target-database` must equal the database the application
   is really connected to.

```bash
createdb omsai_demo
DB_URL=jdbc:postgresql://127.0.0.1:5433/omsai_demo \
DEMO_DATA_ENABLED=true DEMO_DATA_TARGET_DATABASE=omsai_demo \
SERVER_PORT=8091 ./mvnw spring-boot:run
```

```
Demo dataset: done. Created 18 subject(s), 31 course(s), 9 teacher(s),
18 student(s), 31 batch(es) and 36 enrollment(s).
```

**Idempotent** — every record is looked up by its natural key first
(subject/course/teacher/student by name, batch by course + year + name,
assignment by batch + course-subject, enrollment by student + batch +
status). A second run against the same database creates **0** records
and changes nothing (verified live: *"Created 0 subject(s), 0
course(s), 0 teacher(s), 0 student(s), 0 batch(es) and 0
enrollment(s)"*).

The seeder uses the **existing service layer**, so the unique names,
the active-course rule for a batch and the one-active-enrollment rule
apply to the demo data too. No raw SQL insert is used.

### 15.6 Existing data: preserved, and the cleanup candidates reported

**Nothing was deleted, truncated, reset or cleaned.** The development
database received exactly one change: the additive `V8` migration.

`scripts/demo-data-cleanup-report.sh` is a **read-only** report that
lists the possible E2E and debug cleanup candidates with the number of
records that depend on each one:

```
--- 7. Records that are NOT candidates (must be kept) ----------------
students: 2 | teachers: 0 | courses: 9 | subjects: 3 | batches: 2 | accounts: 1
--- 8. The summary of the dependent records of the candidates --------
fee_accounts: 32 | payments: 83 | payment_allocations: 54
class_sessions: 127 | attendance: 62 | timetable_entries: 10
examinations: 22 | enrollments: 161
```

The seven `DBg/FF/FG Course …` rows and the `Debug Subject` are
**unidentified** — they were not written by the committed smoke script,
so they are only reported, never classified as disposable. Everything
with the `E2E` prefix is clearly written by `scripts/e2e-smoke.sh`.
A removal needs its own reviewed script and a backup taken with
`scripts/backup-db.sh`; **no such script was written and nothing was
deleted.**

### 15.7 Tests actually executed

| Gate | Before | After |
|---|---|---|
| `./mvnw -o test` | 315 | **380** (0 failures) |
| `./mvnw -o test -Pbrowser-tests` | 345 | **415** (0 failures) |
| `zsh scripts/e2e-smoke.sh` | 208 checks | **208 checks, 0 failed** |
| `git diff --check` | clean | clean |

70 new tests: 23 subject-service, 10 subject-HTTP, 5 subject-browser,
15 catalogue, 17 seeder.

The browser profile now carries the curated catalogue, so the
real-Chromium suite exercises the catalogue an administrator actually
sees.

### 15.8 Known limitation

A subject that is already taught can never be deleted — that is the
deliberate rule of the task: the course relationships are history.
Deactivation is the way out. There is no per-course "hide this subject
from this course".

---

## 16. Correction (2026-10-21) — the curated dataset is now VISIBLE in the running application

§15 seeded the catalogue correctly, but the application the client
opens still showed the old E2E and debug records. The full diagnosis is
in [`CLIENT_READINESS_AUDIT.md`](CLIENT_READINESS_AUDIT.md) §17; this
section records the outcome.

### 16.1 The root cause

The instance on port 8080 ran with **no environment overrides**, so it
used the **default profile** → the default datasource `omsai` → and
`app.demo.enabled` defaulted to **false**. The curated catalogue was in
`omsai_demo` (and in the browser-test database), but the interface the
client opens never looked there. There was also no way to launch the
application against the demonstration database without hand-assembling
three environment variables.

Measured:

| Database | curated courses | E2E courses | debug courses |
|---|---|---|---|
| `omsai` — the running application | **0** | 84 | 8 |
| `omsai_demo` | **31** | 0 | 0 |
| `omsai_browser` | 31 | 0 | 0 |

### 16.2 What was added

| File | Purpose |
|---|---|
| `src/main/resources/application-demo.yml` | the **demo profile**: the demonstration database, the seeder enabled and the administrator from the environment |
| `scripts/start-demo-app.sh` | the **one command**: `zsh scripts/start-demo-app.sh` — creates the database if needed, refuses any database other than `omsai_demo`, and starts the application with the demo profile |
| `src/test/java/com/omsai/coaching/browser/DemoCatalogueVisibleBrowserTest.java` | a browser test that drives the **running** application (it boots no app of its own and uses no test database) |

The production protection is unchanged: `application-prod.yml` still
pins `app.demo.enabled` to a literal `false`, and the seeder still
refuses in the `prod` profile — verified again live:

```
Demo dataset: not seeded - app.demo.enabled is false. Nothing was written.
```

### 16.3 What the client sees now

The instance on port 8080 runs the demo profile against `omsai_demo`:

| Page | Shows |
|---|---|
| Courses | the **31 curated offerings** in seven categories ("31 record(s)") |
| Subjects | the **18 canonical subjects** with their descriptions |
| Teachers | the **9 fictional teachers** (Sandeep Patil, Neha Kulkarni, Amit Jadhav, …) |
| Students | the **18 fictional students** (Ananya Patil, Vedant Jadhav, Sai Shinde, …) |
| Course detail | exactly the subjects of the offering — PCM/PCB/PCMB, JEE, NEET and the school sets |

Screenshots: `target/browser-artifacts/demo-catalogue-courses.png` and
`target/browser-artifacts/demo-catalogue-subjects.png`.

**After a restart** the counts are identical and the seeder reports
*"Created 0 …"* — the dataset survives a restart and re-seeding
creates no duplicates.

### 16.4 The development database was not touched

`omsai` still holds its 93 courses, 87 subjects, 140 batches, 86
students, 79 teachers, 167 enrollments, 33 fee agreements, 86
payments, 57 allocations, 132 class sessions, 64 attendance rows,
11 timetable entries, 24 examinations and 58 accounts — including
*Aarav Sharma* (`OMS-2026-00001`) and *Dia Patel* (`OMS-2026-00002`)
with their original details. It contains **0** curated courses, **0**
demo teachers and **0** demo students: the seeder never wrote to it.

The old E2E and debug records were **not** deleted and **not** hidden
by a UI filter — the demo application simply runs against a different
database, so what it shows genuinely *is* the curated catalogue.

### 16.5 A second defect the verification found — and the fix

Running the required gate `zsh scripts/e2e-smoke.sh` against the demo
instance wrote its `E2E …` records into the **demonstration**
database, where they showed up in the visible catalogue. The new
`DemoCatalogueVisibleBrowserTest` caught it.

1. **The smoke script now finds the application's database** instead of
   assuming `omsai` (its HTTP calls and its SQL queries had gone to
   two different databases — 98 of the 208 checks failed). It detects
   the database from the record it has just created through the running
   application and prints which one it found. Verified on both paths:
   the demo instance on :8080 → `omsai_demo` (208/208) and a
   development instance on :8091 → `omsai` (208/208).
2. **The demonstration database is reset on every demo start**
   (`scripts/reset-demo-db.sh`, guarded to `omsai_demo` only). It is
   disposable and holds nothing but the curated catalogue, so the
   client always sees exactly that — never the leftovers of a test
   run. The development database is never touched.

After the reset: `e2e=0 debug=0 curated_courses=31 subjects=18
teachers=9 students=18`.

---

## 17. Premium hybrid UI/UX redesign — Phase 1 (2026-10-21)

The visual layer was rebuilt on top of the working application. **No
business rule, security rule, migration or record was touched**; the
figures on the dashboard are the same live queries as before.

### 17.1 What was wrong with the old interface

1. **No design system.** `app.css` was 184 lines of ad-hoc values
   (`#14306b`, `#0d224d`, `#f5f7fb`) with no tokens, no type scale and
   no shared component styles. Every page re-derived its own look.
2. **A white sidebar.** The navigation was a white column with navy
   text, so the product had no brand identity at all, and the institute
   name was invisible next to a blue icon.
3. **A flat menu of 15 links.** No grouping, so scanning the
   navigation meant reading every item.
4. **Broken navigation for two of the three roles.** The sidebar showed
   every administrator link to teachers and students, but
   `SecurityConfig` protects `/students`, `/courses`, `/subjects`,
   `/batches`, `/teachers`, `/fees`, `/payments`, `/installments`,
   `/users` and `/attendance/reports` with `hasRole('ADMIN')`, and
   `/dashboard` with `@PreAuthorize("hasRole('ADMIN')")`. A teacher
   clicking "Dashboard" or "Students" received **403**. The menu now
   shows each role only the destinations it may actually open.
5. **Eight identical dashboard cards in two rows**, so nothing read as
   more important than anything else.
6. **A wall of fourteen outline buttons** ("Management") that
   duplicated the sidebar and looked like a control panel.
7. **No quick actions for the daily work** an administrator actually
   does.
8. **No greeting, no date, no hierarchy** on the dashboard, and the
   "Today's classes" and "Next installments" lists were plain tables
   with no empty state.
9. **Bootstrap defaults everywhere** — default buttons, default
   badges, default tables, no visible focus treatment.

### 17.2 The design system

`src/main/resources/static/css/app.css` was rewritten as one
centralised design system driven by CSS custom properties:

- **Brand tokens** — navy `#172554`, secondary navy `#1E3A5F`, primary
  blue `#2563EB`, indigo `#4F46E5`, saffron `#F59E0B`; surfaces
  `#F8FAFC` / `#FFFFFF`; text `#172033` / `#64748B`; borders
  `#E2E8F0`; status `#15803D` / `#B45309` / `#B91C1C`.
- **Typography** — a system font stack (Inter is not bundled with the
  application, and the interface must not depend on a font CDN), a
  seven-step type scale, and a consistent heading hierarchy.
- **Spacing, radius and elevation scales** — restrained shadows
  (1–2 levels), one border radius family.
- **Component styles** — buttons (primary/outline/secondary/link),
  form controls with a visible focus ring, tables with a small-caps
  header and tabular figures, cards, badges, alerts, dropdowns,
  breadcrumbs, pagination, modals, empty states, stat cards, metric
  grids, quick actions, section titles and a page header.
- **Status is never colour alone** — every badge carries an icon next
  to its label.
- **Accessibility** — visible focus rings on every interactive
  element, `aria-current="page"` on the active navigation entry,
  accessible names on icon-only buttons, and
  `prefers-reduced-motion` support.

The Content Security Policy (`style-src 'self'`, `script-src 'self'`)
was **not** weakened: there are no inline styles and no inline scripts
anywhere; the two new behaviours live in `app.js`.

### 17.3 The shared shell

- **Sidebar** — a navy column with the institute emblem (inline SVG),
  "Om Sai Coaching / Management ERP", grouped navigation
  (*Academic management*, *Daily operations*, *System*), a clear active
  indicator and a **collapse toggle** that turns the column into an
  icon rail with a label tooltip (the choice is remembered in
  `localStorage`).
- **Top bar** — the page title, the signed-in identity with the role,
  and the sign-out control.
- **Responsive** — the sidebar is a sticky column on the desktop, a
  collapsible column on a tablet and an accessible drawer on a phone
  (opened from the labelled menu button in the top bar).

### 17.4 The administrator dashboard

- **Page header** — a greeting derived from the **business clock**
  (`Good morning` / `Good afternoon` / `Good evening`, so it is never
  stale), the institute identity, the current date in the institute's
  timezone and a short explanation.
- **Primary summary cards** — active students, active teachers, active
  batches, class sessions today. All from `DashboardService`.
- **Operations & finance card** — active courses, attendance marks
  today, collections this month, total outstanding. All from
  `DashboardService.phaseTwoStats()`.
- **Installment schedule card** — installments due in 7 days and the
  overdue balance. All from `InstallmentService.overview()`.
- **Quick actions** — register student, create batch, new class
  session, fee accounts. Every destination was checked against the
  controllers; the *Record payment* shortcut was deliberately pointed
  at the fee-account list, because `/payments/new` requires a
  `feeAccountId` and answers **400** without one.
- **Today's sessions and recent enrollments** in a two-column layout,
  with empty states that offer the next step instead of only
  apologising for the empty table.
- The 14-button "Management" wall was removed — the sidebar now
  carries that navigation.

### 17.5 Verification actually performed

| Gate | Result |
|------|--------|
| `./mvnw -o test` | **380 tests, 0 failures, 0 errors** (unchanged baseline) |
| `./mvnw -o test -Pbrowser-tests` | **423 tests, 0 failures, 0 errors** (415 baseline + 3 new design tests + the 5 demo-catalogue checks, which now really run because the demo instance is up) |
| `zsh scripts/e2e-smoke.sh` | **208/208 checks passed** |
| `git diff --check` | clean |
| Real-browser check | `AdminDashboardDesignBrowserTest` (3 tests) — navigation reaches all 14 destinations, the active page is marked, the quick actions work, the dashboard shows live figures, and desktop/tablet/phone show no horizontal overflow and no console error |
| Screenshots | `target/browser-artifacts/admin-dashboard-{desktop,tablet,mobile}.png` |

### 17.6 A regression the E2E smoke found — and the fix

The first run of `zsh scripts/e2e-smoke.sh` after the redesign failed
**4 of 208** checks. All four were the "flash message after a POST"
checks, while the data-persistence checks that followed them passed.

Cause: the smoke script extracts a flash message with the regular
expression `me-1"></i><span>[^<]*`, and the redesigned alert fragment
had put `aria-hidden="true"` between `me-1"` and `></i>`. The message
was displayed correctly in the browser, but the script could no longer
read it.

Fix: the two flash-alert icons keep their original markup, so the
script — and therefore the assertion that a flash message really
appears — works unchanged. The accessibility attributes were kept
everywhere else, where no test depends on the markup.

### 17.7 Honest limitations of this phase

1. **Only the shell and the administrator dashboard were redesigned.**
   Students, teachers, courses, subjects, batches, attendance, fees,
   installments, timetable, examinations, results, login and the
   student portal keep their old markup; they inherit the new design
   system, so they are consistent but not yet re-laid-out. That is the
   scope of the phase.
2. The navigation groups are visual sections, not collapsible groups —
   a collapsible group would hide links that the existing browser test
   clicks directly.
3. The dashboard greeting follows the institute's business timezone,
   not the browser's timezone.
4. The collapse state is remembered per browser (`localStorage`); it is
   not a user preference in the database.
5. No charts were introduced: no charting library is present in the
   project and none was added, because the existing data is tabular and
   the specification forbids decorative charts.
6. The "Class sessions today" card stretches to the height of the
   neighbouring card when no session exists for the day; the empty
   state offers a shortcut, but the card is still tall.
