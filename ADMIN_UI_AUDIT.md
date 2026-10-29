# Admin Interface Audit — Om Sai Coaching ERP

**Date:** 29 October 2026
**Method:** The running `demo` profile was inspected in headless Chromium
(Playwright) through the rendered sign-in form. Every screen was loaded as a
signed-in administrator at 1440×900; the main list/detail screens were also
loaded at 390×844. Layout metrics, console errors, page errors and HTTP errors
were recorded for each load, and full-page and above-the-fold screenshots were
captured. Templates, fragments and `app.css` were then read to confirm the
root cause of each finding.

**Coverage:** 36 unique administrator screens / 49 page loads (36 desktop,
13 mobile): dashboard, students, student detail/new, courses, course detail,
subjects, subject detail, batches, batch detail, teachers, teacher detail,
users, fees (all/outstanding), fee detail, collections, installments/overdue,
sessions, session detail/attendance, attendance reports, timetable,
examinations (list/new/detail/marks), payments (list/new), receipt, notices
(list/new), quizzes (list/detail/new).

**Environment note:** Two disposable records (one draft examination and one
payment) were created in the disposable `omsai_demo` database so the
examination and receipt screens could be audited with real data. The demo was
reset afterwards. Screenshots and raw metrics are in `/tmp/opencode/admin-audit`
(local only, not committed).

---

## 0. What is already working

Do not rebuild these:

- The shared shell is coherent: grouped sidebar, role-aware navigation, active
  state, collapsible desktop rail, mobile drawer and top bar.
- The token-based design system is real and mostly applied: cards, tables,
  badges, focus rings, empty states and alerts look like one product.
- Every audited page returned **200**; there were **no console errors, no page
  errors and no HTTP errors**.
- Icon-only table actions carry `title` and `visually-hidden` text, so they
  have accessible names.
- Empty states exist on all list modules and offer a next step.
- There is **no page-level horizontal scrolling** at 390px (`window.scrollX`
  stays 0); wide tables scroll inside their `.table-responsive` card.
- Filters have `aria-label`s, forms have inline validation and status is never
  colour-only.

The remaining work is finishing the rollout and fixing a small number of
concrete defects — not another redesign.

---

## 1. Findings

The findings below describe the pre-Batch-1 state measured on 29 October 2026.
Section 1a records what Batch 1 changed.

| # | Priority | Finding | Evidence | Recommended change |
|---|---|---|---|---|
| **F1** | **P1** | The weekly timetable is ordered alphabetically by weekday, not Monday-first. | `TimetableEntry.dayOfWeek` is `@Enumerated(EnumType.STRING)`; the repository queries use `order by e.dayOfWeek, e.startTime`. PostgreSQL sorts the strings `FRIDAY, MONDAY, SATURDAY, THURSDAY, TUESDAY, WEDNESDAY`. The weekly screen shows exactly that order. | Order by an ISO index (a `case` expression in JPQL, or a persisted `iso_day` column) then start time; add a repository/browser test that asserts Monday→Sunday. |
| **F2** | **P2** | The shared page header is only half rolled out, and titles are duplicated. | Only 13 templates use `.page-header` (the 12 list pages + quiz questions). **39 other non-print templates** (details, forms, reports) start directly with a card or filter bar. On 13 pages the top-bar `<h1>` and the body `.page-title` say exactly the same thing (e.g. “Students” twice, “Fee agreements” twice). | Add one `pageHeader` fragment and apply it to every admin screen. Rule: the top bar names the **module/section**, the body header carries the **specific page title**, subtitle, status and actions. On forms/details add a back link or breadcrumb. Remove exact duplication. |
| **F3** | **P2** | Fee screens bypass the money formatter. | `fees/list.html` prints `${row.grossFee}`, `${row.outstanding}` etc.; `fees/detail.html` prints `${view.installment().amount}` and `${view.paid}`; `fees/installment-form.html` and `payments/form.html` print raw fee values. The result is `48000.00` next to `₹ 48000.00` elsewhere. | Use `@money.money(...)` in every fee/payment amount, including installment tables and summary rows. Add a rendered-page assertion for the shared rupee/decimal format. |
| **F4** | **P2** | Wide tables hide their important columns on a phone, with no affordance. | At 390px the students table is 566px inside a 361px card; fees 774px; payments 761px; sessions 675px; timetable 821px. The page does not scroll sideways, but status/actions sit off-screen and nothing tells the user to swipe. | Pick one responsive pattern and apply it to the high-traffic lists (students, fees, payments, sessions, timetable): stacked cards below `md`, or a sticky first column plus a visible scroll hint/shadow. Keep the page itself at 390px and make the primary row action reachable. |
| **F5** | **P2** | Date-range filters have no visible labels. | `sessions/list.html` and `payments/list.html` render two bare `type="date"` inputs. They have `aria-label`s/tooltips, but on screen both just say `dd/mm/yyyy`; nothing distinguishes “from” from “to”. | Add visible `From` / `To` labels (or a compact range control) and keep the accessible names. Use the same pattern on every list with a date range. |
| **F6** | **P2** | Forms and detail pages have no page context. | Register student, New notice, New quiz, New examination, Record payment and most edit/details screens open with a card. The only title is in the top bar; there is no subtitle, module eyebrow or back link. | Same header fragment as F2: eyebrow, title, one-line purpose, back/cancel link, and the primary action. On wide screens use a two-column layout: form + a context/help panel, instead of one narrow card on an empty page. |
| **F7** | **P2** | No skip-to-content link. | No `id="main"` or focusable skip link exists in the layout. Keyboard users tab through the whole sidebar on every page. | Add a visually-hidden-focusable “Skip to content” link as the first focusable element, target `#main`, and put `id="main"` on the content landmark. |
| **F8** | **P3** | The timetable card header collides at 390px. | `Classes of the selected week` and the date range are in a non-wrapping `.card-header d-flex justify-content-between`; at 390px the two strings run together. | Allow wrapping (`flex-wrap gap-2`) and let the date range drop to its own line. |
| **F9** | **P3** | Fee-detail primary actions are below the fold. | Record payment / Edit agreement are in the card footer, after a long definition list. On a phone they are several screens down; the first screen is all read-only data. | Move the primary actions into the page header or a sticky action bar; keep only secondary actions in the footer. |
| **F10** | **P3** | “Collection summary” is reachable three times on the fees screen. | It appears in the sidebar (Finance), as a pill in the page header, and as a tab in the list navigation. | Keep one navigation row: All agreements / Outstanding / Overdue / Collection summary. Remove the header pill and the duplicate tab. |
| **F11** | **P3** | Two menu controls with an unclear division. | The top-bar hamburger is visible on desktop (`d-lg-inline-flex`) while the sidebar footer also has a Collapse/Expand control. | Hide the hamburger at `lg` and keep it for the mobile drawer; keep the footer control for the desktop rail. |

### Batch 1 delivery status

Implemented on 29 October 2026:

- **F1 fixed.** `TimetableEntryRepository` orders every timetable query by an
  explicit ISO weekday `case` expression and then start time. A service
  regression test and a rendered `/timetable` assertion both prove
  Monday → Sunday ordering.
- **F3 fixed.** Every fee, installment and payment amount on the list, detail,
  collection, plan, payment-form and receipt screens now goes through
  `@money.money(...)`. The formatter keeps the rupee sign attached to the
  amount with a non-breaking space, so narrow table columns cannot orphan the
  symbol. A rendered-page test asserts the shared rupee format on `/fees` and
  `/payments/new`.
- **F2 rolled out to the audited high-value screens.** A shared
  `pageHeading(icon, eyebrow, title, subtitle)` fragment now feeds the
  examination list/detail/form/marks screens, the weekly timetable, attendance
  reports, fee detail, collections, installment upcoming/overdue, session
  detail/attendance, and the student/notice/quiz/payment forms. Exact
  top-bar/body title duplication was removed from the list pages by giving the
  body heading a specific page title (for example “Students” in the top bar and
  “Student directory” in the page header).
- **F7 fixed.** All 69 application pages with the standard shell now start with
  a `Skip to content` link and expose their content landmark as
  `id="main" tabindex="-1"`.
- **F8 fixed.** The timetable card header wraps its date range at phone
  widths.
- **F9 fixed for fee detail.** “Record payment” and “All payments” moved into
  the page header; the footer keeps the edit/plan actions.

Verification on the final tree:

- `./mvnw -o test` — **403 tests, 0 failures**.
- `./mvnw -o test -Pbrowser-tests` — **442 tests, 0 failures**.
- `zsh scripts/e2e-smoke.sh` — **226 checks, 0 failed**.
- `git diff --check` — clean.

Still open for later batches: the rest of the F2 header rollout (remaining
detail/form/report screens), F4 responsive tables, F5 visible date-range
labels, F6 form context panels, F10 fees navigation cleanup and F11 menu
control split.

### False alarms checked and dismissed

- The full-page screenshots are wider than 390px because Playwright captures
  the scrollable content of nested table containers. The page itself cannot
  scroll sideways (`scrollX` remains 0 after `window.scrollTo(1000, 0)`).
  This is **not** page-level overflow; F4 is about discoverability of the
  off-screen columns, not a broken layout.
- The one button flagged as “icon-only without a label” on the fee page is the
  hidden submit inside the closed void-payment `<details>`; it has visible text
  when opened. It is not an accessibility defect.
- The dashboard does not repeat quick actions in the header; that phase-1
  cleanup holds.

---

## 2. Proposed delivery order

### Batch 1 — correctness and shared header (recommended first)

1. F1 timetable Monday-first ordering + regression test.
2. F3 all money through `@money` + rendered-page test.
3. F2 shared `pageHeader` fragment; apply to the missing high-value screens:
   examinations (list/detail/new/marks), timetable, attendance reports,
   fee detail, session detail/attendance, fees collections, installments
   upcoming/overdue, and the main forms.
4. F7 skip link + `id="main"`.
5. Remove exact top-bar/body title duplication while touching the headers.

**Effort:** small/medium. **Risk:** low (markup and one ordering query).
**Why first:** it fixes the only correctness bug and makes the product feel
finished without touching workflows.

### Batch 2 — phone and detail usability

1. F4 responsive pattern for students, fees, payments, sessions, timetable.
2. F5 visible date-range labels.
3. F8 timetable header wrapping.
4. F9 fee-detail action placement.
5. F10 fees navigation cleanup.
6. F11 desktop hamburger.

**Effort:** medium. **Risk:** low/medium (browser tests at 390px needed).

### Batch 3 — forms

1. F6 two-column form layout on wide screens, single column on phones.
2. Consistent back/cancel and primary-action placement across all forms.
3. Context panels (what this form affects, required fields, links to related
   records) where useful.

**Effort:** medium (many templates, mechanical). **Risk:** low.

### Batch 4 — separate functional decision (not UI-only)

These come from the client-readiness inventory, not this audit:

- single-occurrence timetable reschedule;
- published-result amendment workflow;
- optional global search (student name/code, receipt number) in the top bar;
- CSV export for fee/payment lists (attendance already has export).

Do not bundle these into the visual batches without a separate scope decision.

---

## 3. Acceptance criteria

For every batch:

- Every administrator page returns 200 and has **one clear page title**, with
  no top-bar/body duplication.
- No console, page or HTTP errors on the audited screens.
- At 390px there is no page-level horizontal scroll, and the primary action of
  each row is reachable without guessing that a table scrolls.
- Timetable reads Monday→Sunday, then start time.
- Every currency value uses the same shared rupee/decimal format.
- `./mvnw -o test`, `./mvnw -o test -Pbrowser-tests`,
  `zsh scripts/e2e-smoke.sh` and `git diff --check` are green.
- New browser coverage for: timetable order, money formatting, page-header
  presence/duplication, and the 390px table pattern.

---

## 4. My recommendation

Start with **Batch 1**. It is the smallest slice that removes the “half
finished” feeling: one real bug (timetable order), one real consistency bug
(money), and the shared header/skip-link work that every later page will reuse.
Then do Batch 2, because the phone experience is where an administrator is most
likely to be blocked (actions off-screen). Leave forms (Batch 3) and the
functional items (Batch 4) until Batch 1–2 are verified.
