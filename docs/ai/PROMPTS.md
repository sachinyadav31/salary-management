# AI workflow log

How AI tools (Claude Code) were used, what they produced, and what was corrected.

## Workflow
For each phase: plan → review the plan → implement test-first → run tests → review the diff → commit.

## Log

### Phase 0 — Planning
- Used a chat assistant to turn the brief into `docs/REQUIREMENTS.md` (scope, assumptions, out-of-scope with reasons).
- Clarifying questions sent to Incubyte before building (salary history, currency handling, auth, hosting).

### Phase 1 — Scaffold
- Spring Initializr no longer offers Spring Boot 3, so the scaffold uses Spring Boot 4.1 (Java 21). Docs updated.
- No Chrome on the dev machine → replaced Karma/Jasmine with Angular's Vitest runner (jsdom). Faster, no browser needed.
- Added `hibernate-community-dialects` for SQLite; `ddl-auto=validate` so Flyway owns the schema.

### Phase 4 — Schema and entities
- Anticipated SQLite pitfalls up front: sqlite-jdbc stores dates as epoch millis by default, and NUMERIC affinity turns
  decimals into floats. Fix: JPA converters store `LocalDate`/`Instant` as ISO text and FX rates as decimal strings.
- `ddl-auto=validate` failed (SQLite reports `INTEGER` for 64-bit ids, Hibernate expects `BIGINT`). Turned validation
  off; Flyway owns the schema and `PersistenceMappingTest` round-trips every entity instead.

### Phase 5 — Seed data
- Split into a pure `EmployeeSeedGenerator` (fixed `Random(42)` + fixed as-of date → identical data every run, unit
  tested without Spring) and a `DatabaseSeeder` that does JDBC batch inserts in one transaction.
- Salary history is generated *backwards* from the current salary so the latest record always equals the
  denormalised `current_salary_minor` (verified by a SQL test).
- Measured: 10,000 employees + 18,882 salary records seeded in ~230 ms on SQLite.

### Requirements v0.2 — product direction
- Asked the assistant for a UI that stands out and a backend with strong design, while respecting the brief's
  "not the most complex system, but good engineering judgment".
- Rejected: generating the UI in Lovable (React + Supabase conflicts with the Angular/Java constraint) and adding
  Kafka/Redis/microservices (no load or team to justify them).
- Accepted: features grounded in HR practice (pay bands, compa-ratio, outlier detection) and a polished Angular UI.
  Requirements were updated *before* any code, so the docs lead the implementation.

### Phase 6 — Employee search API
- Design discussed before coding: CQRS-lite (JPA for writes, SQL read model for lists/aggregates), whitelisted
  sort fields, escaped LIKE wildcards, RFC 7807 errors.
- Spotted during design: sorting "by salary" across currencies is meaningless in local money. Added
  `current_salary_usd_minor` (new migration V3, not an edit to V1, since applied migrations are immutable).
- A test I wrote asked for `size=200` and failed. The cap of 100 was working as intended, so the test was fixed, not the code.
- Measured 6–11 ms per query on 10k rows (see `PERFORMANCE.md`).

### Phase 7 — Employee create / update / deactivate
- Plan reviewed before coding: currency derived from country (invalid state unrepresentable), server-generated
  employee codes, two-layer validation (Bean Validation for shape, service for business rules), soft delete.
- Optimistic locking needs two guards: an explicit client-version check (catches a stale *form*; JPA alone can't,
  since each request loads a fresh entity) plus JPA `@Version` (catches an in-flight race at commit). Both map to 409.
- Commands (`EmployeeCommands`) are separate from HTTP DTOs so the domain has no web dependency; the controller
  writes through the service and reads the response back from the query side (CQRS-lite).
- Verified end to end on the 10k dataset: 201 + Location, stale save → 409, duplicate email → 409,
  JPY with decimals → 400, deactivate bumps version.

### Phase 8 — Salary changes with effective-dated history
- Design choice: allow future-dated changes (real HR: "effective 1 April") while keeping the denormalised current
  salary. A `SalaryRolloverJob` (startup + daily) re-syncs employees whose latest effective record changed.
  Idempotent and self-healing. "Denormalisation is a cache; the rollover job is its invalidation."
- Rules live in a pure `SalaryHistory` class (13 unit tests): no dates before hire, max 1 year ahead, HIRE only at
  creation, same-date entries must be CORRECTIONs with a note, latest-recorded wins on ties.
- Removed a package cycle (employee ↔ salary): `EmployeeService` now publishes `EmployeeHired`; `SalaryService`
  writes the HIRE record in a synchronous listener, i.e. the same transaction (deliberately not
  `@TransactionalEventListener`, which runs after commit and could leave an employee without salary history).
- Bugs caught by tests:
  1. A scheduled change didn't bump the version: I relied on dirty-checking `updatedAt`, which is a no-op when two
     writes share a timestamp. Fixed by using `OPTIMISTIC_FORCE_INCREMENT`.
  2. Hibernate applies that at commit, so a rollback-only `@DataJpaTest` can't observe it. Added a committing test,
     which then revealed a *double* increment when the salary also changed. Fixed by forcing only when the row isn't
     already dirty. The contract is now "one salary change = exactly one version bump", pinned by a test.

### Phase 9 — Pay bands and compa-ratio
- Bands per country × level (V5 migration): midpoint from the same model as the seed data, min/max = ±20%.
  A test asserts the migration and the seed model agree for all 48 bands (guarding the duplication).
- Compa-ratio and band position are defined **once**, in the SQL view `employee_pay`, using integer basis points with
  half-up rounding. Search, filters, sort and insights all read it, so the definitions can't drift. A boundary test
  proves 0.969950 → 0.97.
- New filters `bandPosition`, `compaMin`, `compaMax`, and sort `compaRatio`. `GET /api/pay-bands` adds active
  headcount split below/within/above for a band heatmap.
- Seeded workforce: 87% within band, 5.2% below, 7.8% above. Realistic outliers for the dashboard.
- Cleanup during review: a scripted edit left a mis-indented row mapper with fully qualified `java.sql` types;
  tidied before commit.

### Phase 10a — Insights and caching
- Chose in-memory computation over SQL window functions for medians: pure, exact, unit-testable classes
  (`PayStatistics`, `Histogram` with 1/2/5×10ⁿ "nice" buckets, `InsightsCalculator`), with the scaling limit
  documented.
- Cache invalidation is `@TransactionalEventListener(AFTER_COMMIT)`, the deliberate opposite of phase 8's synchronous
  HIRE listener. A generation counter closes the "load overlaps a write" race; a deterministic unit test reproduces it.
- Corrected my own test: it expected two values of 9×10¹⁸ to sum without overflow. The real contract is "realistic
  payrolls fit easily; overflow fails loudly instead of wrapping", so the test now asserts `ArithmeticException`.
- Integration tests with real commits: a committed hire appears on the next read; a rolled-back hire leaves the
  same cached snapshot in place.

### Phase 10b — Outlier spotlight
- Refined the plan before coding: measure every rule in compa-ratio points (1 point = 0.01), so
  severity = people × points is comparable across rule types. The originally planned "max/min salary ≥ 1.6×" spread
  rule became "compa range ≥ 0.50" (same band, so equivalent, but in the shared unit).
- Department lag is org-wide (one card per department) to avoid eight near-identical per-country cards.
- Guards: minimum group size 5 (noise and privacy), sorted group keys and an id tie-break for deterministic output.
- Verified on 10k: the top underpaid card ("35 of 123 Operations Analysts in India") links to a filter that returns
  exactly 35 people. The spotlight also exposes a real policy issue in the data: country × level bands don't fit
  every job family (Engineering clusters above band, Operations below). That makes job-family bands the natural
  next iteration.

### Phase 11a — CSV export
- Same filters and sort as the directory (shared `EmployeeFilterParams` record + shared WHERE builder), so the
  export always matches what's on screen.
- Security: text cells starting with `= + - @`, tab or CR get a `'` prefix (OWASP CSV injection); number cells are
  not guarded, since negative numbers are legitimate. UTF-8 BOM so Excel shows non-ASCII names correctly.
- Refactor during the phase: search and export had 10 duplicated `@RequestParam`s; replaced them with one
  record bound from the query string.
- Verified: 10k rows / 1.7 MB in 171 ms, chunked (streamed); the filtered export of a spotlight card returns exactly
  the card's 35 people.

### Phase 11b — OpenAPI / Swagger UI
- springdoc-openapi 3.1.1 (built against Spring Boot 4.1; checked the POM before adding it).
- API-level description states the conventions once (exact money, USD reporting, RFC 7807, optimistic locking);
  each controller gets a `@Tag`, and each endpoint a one-line `@Operation`.
- `@ParameterObject` on the shared filter record, otherwise Swagger would show the directory filters as one opaque
  object; a test asserts `country`, `level`, `bandPosition`, … appear as individual query parameters.
- Opened `/swagger-ui.html` in the built-in browser to confirm it renders.

### Phase 11c — Architecture tests (ArchUnit)
- Seven executable rules: no feature cycles, framework-free domain classes, controllers never use JPA repositories
  or `EntityManager`, no entities in the API layer, nothing depends on `api`, controllers live in `api`,
  no `double`/`float` fields outside seed data.
- Wrote the rules first and let them fail. They found **three real issues in code written earlier in this session**:
  1. Cycle `employee ↔ payband`: `JobLevel` lived in `employee` but is reference data used by bands. Moved it to
     `reference`.
  2. Cycle `employee ↔ reference`: `MetaController` composed data from both but lived in `reference`. Moved it to a
     new top-level `meta` feature.
  3. (Not predicted) `InsightsController` called JPA repositories to validate filters. Added `ReferenceDataService`.
- Lesson for the AI workflow: plausible-looking generated code still drifts architecturally; executable rules catch
  that drift on every build, not just at code review.

### Phase 12a — UI shell and theme ("Pay Atlas")
- Dark-first M3 theme: every colour is a CSS `light-dark()` pair, so switching `color-scheme` flips the whole app
  (Material included) without duplicate theme CSS. Band colours (coral below / teal within / violet above) are
  global tokens so they mean the same thing on every screen.
- Fonts (Inter, Space Grotesk) and Material Symbols are self-hosted via npm instead of Google CDNs: no third-party
  requests, works offline.
- Typed API models mirror the backend DTOs; one HTTP interceptor turns RFC 7807 problem details into snackbars.
- Theme choice is persisted, with storage access wrapped in try/catch (private mode can't break the app).
- Verified in the built-in browser with the backend running behind the dev proxy (FX date comes from `/api/meta`).
- Small test fix: asserted on `.label` text, not the icon ligature + label `textContent`.

### Phases 12b–12c — Shared UI and employee directory
- Every visual piece is a pure function plus a thin component: `formatMoney` (per-currency locale, e.g. lakh grouping
  for INR), `countryFlag`, `bandBarGeometry` (band plus 25% padding so outliers sit visibly outside). All are unit
  tested, and screen-reader labels describe what the colour shows.
- The URL is the state: filters/sort/page live in query params, parsed tolerantly. Backend filter names were reused
  as URL param names, so outlier cards deep-link with zero mapping code (tested).
- `switchMap` cancels stale requests; the previous page stays visible (dimmed) while the next loads; the search box
  is debounced and uses `replaceUrl` so typing doesn't flood history.
- Verified in the built-in browser on the 10k dataset: quick filter + sort update the URL; the outlier deep link
  returns exactly 35 people like the card; no horizontal page scroll on a 375 px phone. Fixed wrapped action buttons
  on phones found during that check.
- Production build exceeded the 500 kB initial budget by 14 kB (131 kB transferred); raised the warning budget with a
  documented reason rather than contorting the code.
