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
