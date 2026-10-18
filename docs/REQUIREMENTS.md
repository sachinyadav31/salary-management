# Salary Management — Requirements (v0.5, draft)

## Goal
Replace ACME's Excel-based salary tracking with a web app where the **HR Manager** can (1) maintain salary data for ~10,000 employees across countries and (2) quickly answer "how do we pay people?" questions without building spreadsheets.

## Persona
**HR Manager** at ACME: comfortable with Excel, not technical. Today they filter and pivot large sheets by hand. They need correct numbers fast, and to be confident that edits don't silently corrupt the data.

## Assumptions (to be confirmed with Incubyte)
- Only HR staff use the system, all with the same permissions (single HR persona). They must sign in; salary data
  is never visible anonymously.
- Each employee has one **current base salary** in their **country's local currency**. Changes are effective-dated and kept as history.
- Org-wide insights are shown in one **reporting currency (USD)** using a **fixed, dated exchange-rate table**, not live rates.
- No existing Excel schema was provided, so I define the data model.

## In scope
1. **Employee directory**: server-side paginated table with search (name, email, employee code), filters (country, department, job title, level) and sorting.
2. **Employee CRUD**: create, view, edit and deactivate employees, with validation (unique email/code, positive salary,
   currency must match country, plausible hire date). Hiring shows where the starting salary lands in the pay band.
3. **Salary changes with history**: record a change (new amount, effective date, reason: annual review, promotion,
   merit raise, market adjustment or correction). History is append-only, and the current salary is always the latest
   effective record. ACME runs one review cycle: annual reviews take effect on 1 April.
4. **Pay insights dashboard**:
   - Headcount and total annual payroll (USD), overall and per country.
   - Per country: min / max / average / median salary in local currency and USD.
   - Per job title within a country: min / max / average, to spot outliers against peers.
   - Salary distribution (bands) per country or department.
   - **World payroll map**: headcount and payroll per country at a glance; click a country to drill in.
5. **Pay bands and compa-ratio**: each country × level has a pay band (min / midpoint / max). Every employee shows
   their **compa-ratio** (salary ÷ band midpoint) and where they sit in the band. This is standard HR practice for
   judging whether someone is fairly paid.
6. **Outlier spotlight**: the system surfaces groups and people paid well below or above their band (e.g. "14 L3
   engineers in India are >15% below midpoint"), each linking to the filtered employee list.
7. **CSV export** of any filtered employee view, so HR can keep using Excel for ad-hoc analysis.
8. **Sign-in and accountability**: HR staff sign in with email and password; every page and API call requires a
   session. Salary changes and profile edits record **who** made them, shown in the employee's history. Basic
   protection against password guessing (temporary lockout) and session hijacking (HttpOnly cookie, CSRF token).
9. **Career history**: level, title, department and status changes are recorded with who made them and shown in the
   employee's timeline next to salary changes. Raising someone's level prompts HR to record the matching raise.
10. **Variable pay and bonuses**: each employee has a variable-pay target (% of base; defaults by role). Bonuses
    (annual performance payout, spot bonus, sign-on, retention) are one-off awards in an append-only ledger. They don't
    change base salary or compa-ratio (compa-ratio compares base pay to the band). Insights show bonuses paid in the
    last 12 months and total cash compensation.
11. **Bulk changes**: apply a % raise or a one-off bonus to a filtered group (e.g. India · Engineering · L3) with a
    preview first (people affected, payroll cost in USD, band moves, who is skipped and why), then apply it in one
    transaction under the same rules as single changes. A future date makes it a scheduled change.
12. **Seed script**: 10,000 realistic employees across ~8 countries, departments, titles and levels, with country-specific salary ranges. Deterministic (fixed random seed), so tests and demos are reproducible.
13. **Deployment and demo**: a single Docker image deployed publicly, plus a short demo video.

## Deliberately out of scope (and why)
| Left out | Why |
|---|---|
| Roles / fine-grained permissions, SSO | The brief has one persona (HR Manager), so every signed-in user is an HR admin. Spring Security is in place, so a read-only role or company SSO (OIDC) is an incremental change. |
| Payroll processing (tax, payslips, paying salaries and bonuses) | The brief is managing and understanding pay data, not running payroll. Pay Atlas records what was awarded; a payroll system pays it (e.g. fed by the CSV export). |
| Live FX rates | Adds an external dependency and non-determinism. A dated rate table keeps reports reproducible and testable. |
| Equity, allowances, benefits | Base, variable pay and bonuses answer the brief's questions about how people are paid; the model leaves room for other components later. |
| Excel / CSV bulk **import** | Valuable for migration, but needs mapping, validation reports and partial-failure handling. Export is in scope; import is the first candidate for the next iteration. |
| Approval workflows, notifications | Multi-actor features with no second persona in the brief. |
| AI natural-language querying | Dashboards and the outlier spotlight answer the stated questions predictably. Could be layered on the same aggregate API later. |
| Job-family pay bands | v1 bands are country × level. The outlier spotlight shows Engineering clusters above band and Operations below, so per-job-family bands are the next iteration. |
| Microservices, message queues, external cache | One user and 10k rows don't justify the operational cost. A modular monolith with clear feature boundaries can be split later if needed. |

## Non-functional
- **Scale**: 10k employees. Paginate and filter in the database, compute aggregates in SQL with indexes on country/department/title, and keep list and dashboard responses under ~300 ms.
- **Correctness**: money stored as integer minor units plus an ISO currency code, never floating point. Conversion to USD happens in one tested place.
- **Consistency**: a salary change writes the history record and the employee's current salary in one transaction;
  optimistic locking prevents two edits silently overwriting each other.
- **Quality**: fast, deterministic unit tests for domain rules (salary-change validation, current-salary resolution, currency conversion, statistics), plus API tests for the main endpoints. Incremental commits.

## Experience
The HR Manager should *enjoy* using it: keyboard-first (⌘K command palette to find anyone or jump anywhere), fast
feedback (skeleton loaders, instant filters), and visuals that answer questions without reading tables (map, band
bars, salary timeline). A calm, light visual style inspired by Incubyte's design language (forest green, lime
accent, serif headings, pill buttons); the product itself stays ACME's "Pay Atlas".

## Tech choices
Java 21 · Spring Boot 4 · Spring Data JPA · **SQLite** · Flyway migrations · Angular 20 with Angular Material and Apache ECharts · Docker on Render.

## Changelog
- **v0.5**: reviewing the app against this document found two promises missing from the UI: adding an employee
  (the API existed) and the ⌘K search. Both built; hire dates are now limited to 1950 – one year ahead.
- **v0.4**: one 1 April review cycle, merit raises, career history, variable-pay targets, bonus ledger, bulk raise or
  bonus with preview. Driven by HR practice: hikes and bonuses for good work, and changes applied to whole groups.
- **v0.3**: sign-in with session cookies, audit of who changed salaries and profiles, light-only theme.
  Moved authentication from out of scope to in scope: anonymous access to salary data isn't acceptable even for a demo.
- **v0.2**: added pay bands and compa-ratio, outlier spotlight, world payroll map, CSV export, concurrency rule and the
  experience goals. These turn "show me the numbers" into "tell me who is paid unfairly".
- **v0.1**: initial scope and assumptions.
