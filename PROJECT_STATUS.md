# PROJECT STATUS — Om Sai Coaching ERP, Phases 1–3

Date: 2026-10-20
Scope: everything in the Phase 1 and Phase 2 specifications that was
actually built and verified, the Phase 3 real-browser verification and
CI setup, and an honest account of gaps, limitations and the defects
found (and fixed) during development.

---

## 1. Bottom line

Phases 1–3 are implemented and work end to end:

- **`mvn test`: 142 tests, 0 failures, 0 errors, 0 skipped** (green)
  against the `omsai_test` PostgreSQL database.
- **Real-browser Playwright suite: 13 tests, all pass** — see §4. A
  combined `mvn test -Pbrowser-tests` run executes **155 tests** (142 +
  13) in one go, all green.
- **E2E smoke suite: 127/127 checks passed** (`zsh scripts/e2e-smoke.sh`
  against a locally running app), re-verified after the Phase 3
  template fixes.
- **Browser-level print verification succeeded**: Chromium print-media
  emulation and `page.pdf()` output parsed with PDFBox confirm receipts
  hide page controls, render the VOIDED stamp on voided payments and
  carry the correct receipt text (§4, §7).
- All reports above are from real runs on this machine, **not estimates
  from CI**. CI is configured in `.github/workflows/ci.yml` but has not
  been executed (no remote configured — see §8 limits).
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
  see §4 and §7. What *remains* unverified is WebKit/Firefox rendering
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
- No data backfill was needed: Phase 1 data has no attendance/fees.
- Applied to `omsai` (dev) and `omsai_test`; Flyway validate passes at
  boot against both, and the fresh-database path was re-verified on
  `omsai_test`.

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

**Total: 142 tests, 0 failures, 0 errors, 0 skipped.**

**Phase 3 real-browser suite (13 tests, all pass; `mvn test
-Pbrowser-tests`, boots the app on a random port against the disposable
`omsai_browser` database and drives headless Chromium via Playwright for
Java 1.63.0):**

| Suite                       | Tests | What it proves |
|-----------------------------|-------|----------------|
| AuthenticationBrowserTest   | 4     | valid login reaches the dashboard topbar; wrong password shows "Invalid email or password."; protected pages redirect anonymous users to /login; logout lands on /login?logout and the session is really invalidated |
| CorePagesBrowserTest        | 3     | every sidebar link leads to the right management page with its topbar title; course form rejects an empty name and creates a valid course with the flash message; console stays free of errors |
| AttendanceBrowserTest       | 3     | admin creates a session, records per-student attendance, completes it and reads the student report with the exact counts/percentage; completed sessions reject teacher attendance and show the lockout notice, cancelled sessions refuse attendance; a teacher works only on their own assigned sessions, is blocked (403) from fees/payments/reports/users and never sees those sidebar links |
| FeesAndReceiptBrowserTest   | 3     | a partially paid agreement shows the recalculated "Outstanding balance"; the receipt shows amount, method and cheque reference and matches the sequence format `OMS-RCP-2026-######`; voiding marks ledger and receipt VOIDED with reason — verified on screen, under print emulation and via `page.pdf()` / PDFBox text extraction |

Print verification detail (verified in the fees suite): under
`page.emulateMedia({media: "print"})` the `.no-print` controls compute
`display: none`, the receipt renders the clean sheet with its balance
note; `page.pdf()` produces real PDFs (`receipt-printed.pdf`,
`receipt-voided-printed.pdf` in `target/browser-artifacts/`) whose
extracted text contains the receipt number, amount `4000.00`/`5000.00`
and the `VOIDED` stamp for the voided payment. Screenshots of the screen
and print renderings are saved next to the PDFs on every run.

**Combined run: `mvn test -Pbrowser-tests` = 155 tests, 0 failures, 0
errors, 0 skipped** (unit + service + browser in one Maven invoke).

E2E smoke suite (`zsh scripts/e2e-smoke.sh`, final run **127 checks,
0 failed**, re-run after the Phase 3 template changes) — real HTTP flows
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

## 5. Defects found during Phase 2 — and fixed

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

## 6. Phase 1 defects (for completeness)

1. Rate limiter off-by-one (fixed in Phase 1).
2. Teacher `/` redirect could not work (fixed in Phase 1).
3. Teacher detail page crash on `linkedUser` proxy (fixed in Phase 1).
4. Batch detail page crash on `course` proxy (fixed in Phase 1).
5. Invisible `endDateAfterStart` validation error (fixed in Phase 1).
6. Thymeleaf 3.1.5's restricted `new`-word fragment parsing (worked
   around in Phase 1).

---

## 7. Phase 3 defects — real-browser bugs found and fixed

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

## 8. Environment & honest limitations

- PostgreSQL 18.4 local cluster on port **5433** (databases `omsai` and
  `omsai_test`, user `tojo`/`omsai_dev` via `application.yml` defaults),
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
- **Money is `NUMERIC(12,2)` and BigDecimal end-to-end**; display
  formatting is the plain BigDecimal string (no currency formatter).
  This is intentional for honest arithmetic (no rounding surprises), but
  a locale-aware formatter would be nicer eventually.
- **No production hardening checklist yet** (TLS/proxy, secure session
  cookie behind HTTPS, log aggregation, backups) — operator tasks.
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

## 9. Suggested next steps (Phase 4+)

1. Attendance export (CSV) and a printable batch attendance sheet.
2. Installment plans / payment schedules with automated overdue flags.
3. Fine-grained per-teacher report access (if teachers ever need their
   own subject's report), with the same service-level enforcement.
4. Wire CI to a real remote (the workflow is ready in
   `.github/workflows/ci.yml`) and optionally add a WebKit/Firefox
   cross-browser matrix to the Playwright suite.
5. Docker one-command environment (compose + Testcontainers).

*(The former "browser-rendering verification" suggestion from Phase 2 is
complete — that was Phase 3.)*
