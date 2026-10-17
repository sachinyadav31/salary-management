# Decisions and trade-offs

Short records of the choices that shaped Pay Atlas: what was decided, what else was considered, and what it costs.
The goal throughout was the brief's "not the most complex system, but good engineering judgment".

## Architecture

### 1. Modular monolith, one deployable
- **Decision**: one Spring Boot app serving the API and the Angular build from the same origin; features as packages
  with boundaries enforced by ArchUnit.
- **Alternatives**: microservices, separate frontend hosting.
- **Why**: one HR persona and 10k rows don't justify network boundaries, distributed transactions or extra
  infrastructure. Same origin also means no CORS and allows a simple, secure session cookie.
- **Trade-off**: scaling is vertical. Boundaries are already enforced, so a feature could be extracted later.

### 2. SQLite with Flyway
- **Decision**: SQLite file database; schema owned by versioned Flyway migrations (V1–V10), never edited after commit.
- **Alternatives**: PostgreSQL, H2.
- **Why**: the brief suggests SQLite; zero setup; fast enough (directory queries 6–35 ms on 10k rows).
- **Trade-off**: a single writer and no network access, fine for one HR user on one instance. Moving to Postgres means
  swapping the driver and dialect, not the code. Hibernate's schema validation is off because SQLite reports
  `INTEGER` for 64-bit ids; a round-trip mapping test compensates.

### 3. CQRS-lite: entities for writes, SQL for reads
- **Decision**: writes go through JPA entities and services with domain rules; lists, filters, CSV and pay bands use
  hand-written SQL that selects only what's shown.
- **Why**: rich rules where data changes; lean, index-friendly queries where data is read (no N+1, no hydration).
- **Trade-off**: two models of an employee. Kept small, and both are covered by tests.

## Money and data

### 4. Money as integer minor units + currency
- **Decision**: `long` minor units and ISO 4217 currency everywhere (database, Java, JSON); decimals only for display.
  Conversion to USD happens in exactly one place (`CurrencyConverter`, half-even rounding). An ArchUnit rule forbids
  `double`/`float` fields outside seed data. The UI parses typed amounts with string arithmetic.
- **Why**: floating point can't represent most decimal amounts (`0.29 * 100 = 28.999…`); JPY has no minor unit.

### 5. Fixed, dated FX rates
- **Decision**: a rates table dated 2026-10-01; every USD figure says which rates it used.
- **Alternatives**: a live FX API.
- **Why**: reproducible reports and deterministic tests; no external dependency.
- **Trade-off**: rates drift from the market until updated. A rate change requires re-computing the denormalised
  USD column (see 6).

### 6. Denormalised current salary (local and USD)
- **Decision**: the employee row stores the salary currently in effect, in local currency and in USD.
- **Why**: sorting by salary across currencies and USD aggregates become plain indexed integer columns; no
  "latest record per employee" subquery on every read.
- **Trade-off**: it's a cache, so it needs an invalidation story: every salary write updates it in the same
  transaction, and a rollover job (startup + daily, idempotent) applies scheduled changes when they fall due.

### 7. Effective-dated, append-only salary history
- **Decision**: changes are never edited; a mistake is fixed by a `CORRECTION` record that must explain why. Future
  dates (up to a year) are allowed and shown as *scheduled*. Rules live in the pure `SalaryHistory` class.
- **Why**: auditability, and it matches how HR works ("annual review effective 1 April").

### 8. Pay bands per country × level; compa-ratio in a SQL view
- **Decision**: band min/mid/max per country and level; compa-ratio (salary ÷ midpoint) and band position defined
  once in the `employee_pay` view, in integer basis points.
- **Why**: one definition shared by search, sort, filters and insights, so they can't disagree. The UI preview uses
  the same integer formula.
- **Trade-off**: country × level bands ignore job families. The outlier spotlight shows the effect (Engineering
  clusters above band, Operations below), which makes job-family bands the natural next iteration.

## Consistency and concurrency

### 9. Optimistic locking with two guards
- **Decision**: the client sends the `version` it read; the service rejects a mismatch with 409, and JPA `@Version`
  catches a race at commit. A salary change always bumps the employee version exactly once.
- **Why**: a stale *form* can't be detected by `@Version` alone, because each request loads a fresh entity.
- **UX**: on 409 the drawer loads the latest version, shows what changed, and keeps the user's input. Nothing is
  overwritten silently, and nothing typed is lost.

### 10. Domain events: synchronous inside, after-commit outside
- **Decision**: `EmployeeHired` is handled synchronously, so the HIRE salary record is written in the same
  transaction (and the employee → salary package cycle disappears). `WorkforceChanged` invalidates the insights cache
  after commit.
- **Why**: in-transaction work must be atomic; cache invalidation before commit would let a reader cache stale data.

## Insights

### 11. In-memory snapshot + pure calculators, invalidated on commit
- **Decision**: load the active workforce once (≈1 MB), compute statistics in pure Java (exact medians, histograms with
  "nice" buckets, outlier rules), cache until a write commits. A generation counter ensures a load that overlapped a
  write is never served.
- **Alternatives**: SQL window functions for medians; Redis/Caffeine.
- **Why**: SQLite has no `MEDIAN()`; pure functions are exact and easy to test; one cached object on one instance
  needs no cache server.
- **Trade-off**: fine up to roughly 100k employees; beyond that, aggregate in SQL or keep summary tables.

### 12. Outlier spotlight as explicit rules
- **Decision**: four documented rules (underpaid cluster, overpaid cluster, wide spread, department lag), one unit
  (compa-ratio points) so severity is comparable, minimum group size 5 (noise and privacy), and each card links to
  the exact directory filter.
- **Alternatives**: an AI/natural-language query feature.
- **Why**: predictable, explainable and testable answers to "who is paid unfairly?".

## Security

### 13. Session cookie + CSRF, not JWT
- **Decision**: Spring Security sessions (HttpOnly, Secure, SameSite=Lax cookie), `csrf.spa()` (XSRF-TOKEN cookie
  echoed in a header), problem-JSON 401/403, a new session id at sign-in, a 30-minute idle timeout.
- **Alternatives**: JWT in `localStorage` or memory.
- **Why**: same-origin SPA; an HttpOnly cookie can't be stolen by XSS, and logout really ends the session.
- **Trade-off**: server-side sessions are in memory, so a restart (or the free tier waking up) signs users out. With
  multiple instances, use Spring Session (JDBC/Redis) or company SSO.

### 14. Single HR role; audit trail
- **Decision**: the brief has one persona, so every signed-in user is an HR admin. Every salary change and profile
  edit records who made it.
- **Why**: authorization that matches the problem; accountability is what HR actually needs from sign-in.
- **Next**: a read-only role or SSO (OIDC) is an incremental change on the existing Spring Security setup.

### 15. Basic hardening
- Same message for wrong password and unknown email (and a dummy hash for unknown users, so timing doesn't reveal
  accounts); lockout after 5 failures (429 + `Retry-After`, rounded up); CSV export neutralises spreadsheet
  formulas; `returnUrl` limited to in-app paths; API docs and health are the only public endpoints besides the app
  shell. Trade-off: a per-account lockout can be abused to lock someone out for a few minutes; production would add
  per-IP limits or rely on SSO.
- Demo accounts with a published password are seeded for reviewers. A real deployment would never do this.

## Frontend

### 16. Angular 20 with signals, Material, ECharts, Vitest
- Standalone components and signals; Angular Material for accessible primitives; ECharts imported modularly in lazy
  chunks only (a test checks every series type used is registered). Vitest + jsdom instead of Karma: no browser
  needed, fast, and the Angular team's direction.
- **The URL is the state** (filters, sort, page, open employee, selected country): shareable views, working Back
  button, and outlier cards deep-link with zero mapping code.

### 17. Theme
- A light theme inspired by Incubyte's design language (forest green, lime accent, serif headlines, pill buttons),
  built entirely from design tokens, so re-theming was a token change. The product stays ACME's "Pay Atlas":
  Incubyte's logo and name aren't used as product branding.

## Delivery

### 18. Docker, CI, Render free tier
- **Decision**: a multi-stage Docker image (Node build → Gradle build → small JRE as non-root). GitHub Actions run
  backend tests, frontend tests and the production build, then build the image. Render deploys `main` after checks
  pass.
- **Trade-offs**: the free tier sleeps after ~15 minutes (first request takes ~1 minute) and has an ephemeral disk, so
  the database resets on each deploy and the deterministic seed recreates the same 10,000 employees. Edits made in the
  demo don't survive a restart. Production would use a persistent disk or Postgres.

### 19. Tests as the safety net for AI-assisted work
- ~210 backend tests (unit, slice, integration, architecture) and ~80 frontend tests. The AI-assisted workflow and
  every bug the tests or live checks caught are logged in [`ai/PROMPTS.md`](ai/PROMPTS.md).

## Compensation (added after the first review)

### 20. Pay = base + variable target + bonus ledger
- **Decision**: base salary keeps its effective-dated history; a **variable-pay target** is a % of base stored per
  person in basis points (role defaults: Sales 20%, L1–L3 10%, L4 12%, L5 15%, L6 20%); **bonuses** are an
  append-only ledger (performance payout, spot, sign-on, retention). A **merit** raise is its own reason, so "raise
  because of great work" is distinguishable from the yearly review or a market fix.
- **Alternatives**: one "total compensation" number; bonuses as salary records.
- **Why**: HR asks different questions of each part ("who is under band?" is about base; "what did we pay out?" is
  about bonuses). Bonuses never move base salary or compa-ratio.
- **Out of scope**: payroll runs (paying people), equity, allowances and benefits.

### 21. Bonuses don't need an optimistic-lock version
- **Decision**: awarding a bonus inserts a ledger row and doesn't modify the employee, so it doesn't check or bump
  the employee's version.
- **Why**: two HR people awarding bonuses at the same time can't overwrite each other; both awards are real. Locking
  would only create false conflicts. Career events, in contrast, come from profile edits, which are versioned.

### 22. Bulk changes: preview, then one idempotent transaction
- **Decision**: a raise or bonus for everyone the directory filters select. The pure `BulkPlanner` reuses
  `SalaryHistory.checkCanAdd` and `BonusRules.check`, so a bulk change can't do anything a single change couldn't;
  people who don't qualify are skipped with a reason, not failed. Apply re-plans inside its transaction, writes JDBC
  batches, bumps each raised employee's version, and records the client's request id so a double click or retry
  applies once. Amounts round half-up to whole currency units.
- **Alternatives**: a background job with progress; applying the preview's numbers as sent by the client.
- **Why**: 10k people take ~0.3 s in one transaction, so a job queue would be complexity without benefit;
  all-or-nothing is easier to reason about than a half-applied raise. Never trusting the preview means a change made
  between preview and apply is respected.
- **Trade-off**: SQLite has one writer, so other writes wait during the ~0.3 s. Promotions, corrections and
  sign-on bonuses stay per-person decisions and are rejected in bulk.
