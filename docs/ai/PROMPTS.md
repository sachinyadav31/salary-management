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
