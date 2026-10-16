# Salary Management — Requirements (v0.2, draft)

## Goal
Replace ACME's Excel-based salary tracking with a web app where the **HR Manager** can (1) maintain salary data for ~10,000 employees across countries and (2) quickly answer "how do we pay people?" questions without building spreadsheets.

## Persona
**HR Manager** at ACME: comfortable with Excel, not technical. Today they filter and pivot large sheets by hand. They need correct numbers fast, and to be confident that edits don't silently corrupt the data.

## Assumptions (to be confirmed with Incubyte)
- One trusted HR Manager uses the system. Auth/RBAC is not required for this version.
- Each employee has one **current base salary** in their **country's local currency**. Changes are effective-dated and kept as history.
- Org-wide insights are shown in one **reporting currency (USD)** using a **fixed, dated exchange-rate table**, not live rates.
- No existing Excel schema was provided, so I define the data model.

## In scope
1. **Employee directory**: server-side paginated table with search (name, email, employee code), filters (country, department, job title, level) and sorting.
2. **Employee CRUD**: create, view, edit and deactivate employees, with validation (unique email/code, positive salary, currency must match country).
3. **Salary changes with history**: record a change (new amount, effective date, reason, e.g. promotion or annual review). History is append-only, and the current salary is always the latest effective record.
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
8. **Seed script**: 10,000 realistic employees across ~8 countries, departments, titles and levels, with country-specific salary ranges. Deterministic (fixed random seed), so tests and demos are reproducible.
9. **Deployment and demo**: a single Docker image deployed publicly, plus a short demo video.

## Deliberately out of scope (and why)
| Left out | Why |
|---|---|
| Authentication / roles | Single-persona brief. Adding auth well (SSO, roles) is a project in itself. Designed so Spring Security can be added later. |
| Payroll processing (tax, payslips, payments) | The brief is managing and understanding salary data, not running payroll. |
| Live FX rates | Adds an external dependency and non-determinism. A dated rate table keeps reports reproducible and testable. |
| Bonus / equity / allowances | Base salary answers the core questions. The model leaves room for components later. |
| Excel / CSV bulk **import** | Valuable for migration, but needs mapping, validation reports and partial-failure handling. Export is in scope; import is the first candidate for the next iteration. |
| Approval workflows, notifications | Multi-actor features with no second persona in the brief. |
| AI natural-language querying | Dashboards and the outlier spotlight answer the stated questions predictably. Could be layered on the same aggregate API later. |
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
bars, salary timeline). Light and dark themes.

## Tech choices
Java 21 · Spring Boot 4 · Spring Data JPA · **SQLite** · Flyway migrations · Angular 20 with Angular Material and Apache ECharts · Docker on Render.

## Changelog
- **v0.2**: added pay bands and compa-ratio, outlier spotlight, world payroll map, CSV export, concurrency rule and the
  experience goals. These turn "show me the numbers" into "tell me who is paid unfairly".
- **v0.1**: initial scope and assumptions.
