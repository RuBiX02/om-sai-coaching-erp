# UI/UX Audit — Om Sai Coaching ERP

**Date:** October 2026
**Method:** The running application (dev profile, `http://127.0.0.1:8080`) was inspected in a real
Chromium browser via Playwright. Screenshots were captured at desktop (1440×900) and mobile
(390×844) widths for the login page, administrator dashboard, and the students list, and the
Thymeleaf templates, `static/css/app.css`, `static/js/app.js` and the layout fragments were read
in full. Every finding below points at real markup or a real screenshot observation.

---

## 1. Findings

### F1. The login screen is not composed — it is "stuck"
The login card hugs the left edge of an otherwise empty dark-navy void, and the
"Access is limited to administrators…" footnote floats alone mid-screen at an unrelated
position. The brand is a bare Bootstrap `bi-mortarboard` glyph. There is no visual link between
the sign-in form and the product it opens. (Screenshot: `login-before.png`.)

### F2. The shell is the generic "dark admin template" formula
A flat navy slab (`#14306b`) on the left, a full-bleed bright-blue rectangle for the active item,
10px group labels, and a strange full-width "Collapse menu" bar pinned to the sidebar bottom.
It is the single most recognizable "college admin template" signal, and it makes the product
look like a thousand others.

### F3. The dashboard has no hierarchy — seven equal white boxes
Four separate stat cards, an "Operations & finance" 2×2 number dump, a quick-actions panel, a
"Next installments due" table, an "Installment schedule" card, a "Class sessions today" panel and
a "Recent enrollments" table — all rendered as identically-weighted white rounded boxes. Nothing
tells the eye what to read first, and "Register student" appears twice on the same screen
(header button + quick action).

### F4. Metric presentation is inconsistent
Stat icons ship in four different colours and shapes (blue, indigo, amber, green). The
"Class sessions today" card carries a three-line hint while the other three carry one line, so
the card row is ragged. "Operations & finance" mixes counts and rupee amounts with no structure,
and the ₹ amounts are formatted inconsistently across the page: `₹ 540000.00` (no thousands
grouping) in one panel, bare `10000.00` (no rupee sign at all) in the installments table.

### F5. The most actionable information is buried
"22 overdue installments — ₹ 110000.00 overdue balance" is the one figure an administrator must
act on, yet it sits in a small side card *below the fold*, while quick-actions occupy prime
space. There is no "what needs my attention" surface at all.

### F6. Voids instead of designed empty states
When no sessions exist, "Class sessions today" renders a tall empty box containing a lone icon
and a button — a huge white void. Empty states elsewhere are one-line sentences with no icon and
no next step. There is no shared empty-state language.

### F7. Lists are database tables with a Bootstrap skin
The students list: a bare search input floating unaligned above the table (no filter bar), a
green "Active" pill repeated twenty times without conveying anything, three tiny unlabeled
icon-buttons per row, and names as loud blue links that visually dominate every row. Codes are
long unbroken monospace strings. The page has no in-content title — "Students" exists only in
the topbar.

### F8. Typography does no work
The default system stack (no Inter), no tabular numerals outside two classes, no type scale
rhythm: topbar title is a timid `h5`, table headers are cramped uppercase, page titles vary
between modules, numbers do not align in columns. Nothing about the type says "designed".

### F9. The topbar carries almost no information
It holds a small page title, a user chip whose avatar initial reads like a zero, a redundant
"Om Sai Administrator / ADMINISTRATOR" double-label, and a detached grey "Sign out" button. No
date, no context, no grouping of identity + sign-out into one coherent control.

### F10. Mobile is a vertical dump of desktop panels
At 390px the dashboard stacks every panel in one column: metric cards in awkward 2-up pairs with
clipped text, tables that overflow with columns cut mid-word ("DUE DA…"), duplicated panel
headers, and the topbar truncating to "Administrator …". The mobile experience was clearly not
designed, merely allowed to stack.

### F11. Colour is doing contradictory jobs
Navy sidebar + bright blue active pill + green badges + amber icon + brown/amber stat icon +
indigo icon — six accent hues in one viewport with no system. Saffron (the brand's warm accent)
appears only as a tiny dot in the logo.

### F12. No page-header system; actions drift
Some pages open with a full page-header (dashboard), others jump straight to a filter row
(students), others straight to a card (sessions). "Register student" sits at the far right edge
while the search field sits left with nothing connecting them. Module pages have no consistent
opening composition.

### F13. Data that deserves emphasis gets none
Enrollments render raw E2E seed names ("E2E Student Two 20261021180404") as huge blue links —
real users will have long names too, and the row has no typographic hierarchy (name / code /
date all similar weight). Money columns are not tabular-aligned in several tables.

---

## 2. Root cause

The previous pass **re-skinned Bootstrap** (new palette on the same components) instead of
designing the product: the same card grid, the same table skeleton, the same sidebar formula.
Composition, hierarchy, and task flow were never re-thought, so the result reads as "themed
Bootstrap", not "an operations platform".

---

## 3. New visual direction

**A light academic operations platform.** Confident, calm, uncluttered:

1. **Light navigation rail, not a navy slab.** White sidebar on a `#F5F6FA` canvas, deep-ink
   text, compact grouped navigation, soft-blue active pill with a saffron marker. Distinctive
   *and* lighter than the old formula.
2. **One metric band, not six cards.** A single cohesive panel with hairline-divided cells;
   finance figures emphasized; all numerals tabular.
3. **The dashboard answers four questions in order:** what is happening today (schedule
   timeline) → what needs attention (overdue/outstanding panel) → enrollment & attendance
   (metrics) → money (collections, next installments, recent enrollments).
4. **A real page-header system** on every module: eyebrow, title, subtitle, meta chip, actions —
   always in the same grid position.
5. **Inter, self-hosted**, with tabular numerals for all figures and a strict type scale.
6. **The mission palette exactly:** bg `#F5F6FA`, surface `#FFFFFF`, text `#172033`, muted
   `#667085`, blue `#3659E3`, ink `#18243B`, blue-soft `#EDF2FF`, saffron `#F4A62A`, success
   `#16803D`, warning `#B7791F`, danger `#C62828`, border `#E3E7EF`. Saffron is used sparingly:
   brand mark, active-nav marker, page-header meta icon, hero accents.
7. **Indian money formatting** (`₹ 5,40,000.00`) everywhere, from one formatter.
8. **Purpose-built module compositions:** filter bars for lists, timelines for schedules,
   attention panels for finance, roster tables for attendance — one system, not one skeleton.

---

## 4. Implementation map

| Layer | Action |
|---|---|
| `static/css/app.css` | Full rewrite: tokens, Inter @font-face, shell, metric band, timeline, attention panel, filter bar, tables, badges, forms, login, responsive |
| `static/fonts/` | Self-hosted Inter 400/500/600/700 (latin, woff2) — allowed by CSP (`font-src` falls back to `default-src 'self'`) |
| `layout/fragments.html` | New sidebar (grouped, collapsible rail), coherent topbar (identity chip + sign-out), page-header/pager/badge fragments kept API-compatible |
| `dashboard/dashboard.html` | Rebuilt around the four questions with real backend data only |
| `auth/login.html` | Recomposed centered card, real brand mark, ink backdrop |
| `common/MoneyFormat.java` | Indian grouping (`₹ 5,40,000.00`) — presentation only |
| Module templates | Page headers, filter bars, money formatting, badge/empty-state consistency sweep |
| Browser tests | `AdminDashboardDesignBrowserTest` updated to assert the new composition (same intent: nav works, real figures, responsive, no overflow/console errors) |
