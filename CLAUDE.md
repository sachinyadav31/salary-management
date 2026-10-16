# CLAUDE.md — project rules for AI assistants

Salary management app for an HR Manager (~10k employees, multiple countries).
Requirements: `docs/REQUIREMENTS.md`. Design notes: `docs/`.

## Stack
- Backend: Java 21, Spring Boot 3, Spring Data JPA, Flyway, SQLite (Hibernate community dialect), Gradle (`backend/`).
- Frontend: Angular (standalone components) + Angular Material (`frontend/`).
- Deploy: single Docker image (Spring Boot serves the Angular build) on Render.

## Conventions
- Package by feature: `employee`, `salary`, `insights`, `reference`, `seed`, `common`.
- Domain logic in plain Java classes (no Spring) so it is unit-testable.
- REST under `/api`; errors as RFC 7807 problem details; Bean Validation on inputs.

## Money rules
- Store money as integer **minor units** (`long`) + ISO-4217 currency code. Never `double`/`float`.
- Respect currency exponent (JPY = 0 decimals).
- Conversion to the reporting currency (USD) happens only in `CurrencyConverter`, using the dated FX table.

## Test rules
- Test-first for domain logic. Tests must be fast (no network, no sleeps) and deterministic
  (inject `Clock`, fixed random seed).
- Run `./gradlew test` (backend) and `npm test` (frontend) before every commit.

## Commit rules
- Small, incremental, conventional commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`).
- Never commit code that hasn't been read and reviewed.
- Log notable prompts and AI corrections in `docs/ai/PROMPTS.md`.
