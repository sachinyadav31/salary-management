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
