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
