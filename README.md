# Pay Atlas: salary management for ACME's HR team

A web app that replaces ACME's salary spreadsheets for **10,000 employees across 8 countries**, and answers the
question HR actually cares about: **how do we pay people, and is it fair?**

**Live demo:** https://salary-management-sg7p.onrender.com
(free tier: the first visit after ~15 idle minutes takes about a minute to wake up)

| Demo account | Password |
|---|---|
| `hr.admin@acme.example` (Priya Sharma) | `PayAtlas!2026` |
| `alex.morgan@acme.example` (Alex Morgan) | `PayAtlas!2026` |

The demo data is synthetic and resets on every deploy.

## What it does

- **Pay insights**: headcount, payroll (USD), median salary and how many people are paid within their band; a world
  payroll map; per-country and per-title statistics; salary distributions in local currency.
- **Outlier spotlight**: ranked findings such as *"35 of 123 Operations Analysts in India are paid below their pay
  band"*, each linking to exactly those people.
- **Employee directory**: search, filters (country, department, level, status, band position, compa-ratio), sorting
  and paging done in the database; every view is a shareable URL; export the filtered list to CSV.
- **Employee detail**: pay band bar and compa-ratio, salary timeline, history with who recorded each change.
- **Salary changes**: immediate, back-dated or scheduled (applied automatically on their date), with a live preview
  of the % change and new compa-ratio; corrections must explain why.
- **Safe editing**: optimistic locking. If a colleague saved first, you see what they changed and your input is kept.
- **Sign-in**: session cookie with CSRF protection, lockout after repeated failures, and an audit trail of who changed
  what.

## Tech stack

Java 21 · Spring Boot 4 · Spring Security 7 · Spring Data JPA · SQLite · Flyway · Angular 20 (signals) · Angular
Material · Apache ECharts · JUnit 5 / AssertJ / ArchUnit · Vitest · Docker · GitHub Actions · Render

## Run it

**With Docker** (closest to production):

```bash
docker build -t pay-atlas .
```

```bash
docker run --rm -p 8080:8080 -e SESSION_COOKIE_SECURE=false pay-atlas
```

(`SESSION_COOKIE_SECURE=false` because this is plain HTTP; the deployed site uses HTTPS and keeps the cookie `Secure`.)

Open http://localhost:8080 and sign in with a demo account. 10,000 employees are seeded on start-up (deterministic,
fixed random seed).

**For development** (JDK 21 and Node 22):

```bash
cd backend && ./gradlew bootRun
```

```bash
cd frontend && npm ci && npm start
```

The Angular dev server runs on http://localhost:4200 and proxies `/api` to the backend on port 8080.
API documentation: http://localhost:8080/swagger-ui.html

## Test it

```bash
cd backend && ./gradlew test
```

```bash
cd frontend && npm test
```

~170 backend tests (domain unit tests, web slices, integration tests against SQLite and the real security chain,
and ArchUnit architecture rules) and ~70 frontend tests (Vitest). CI runs both, plus the production build and the
Docker image, on every push.

## Project layout

```
backend/    Spring Boot app, packaged by feature: employee, salary, payband, insights, reference, auth, meta, seed, common
frontend/   Angular app: core (API, auth), shared (UI pieces, charts), features (insights, employees, auth)
docs/       Requirements, design, decisions, performance, deployment, AI workflow log
Dockerfile  Multi-stage build: Angular → Spring Boot jar → small JRE image
```

## Documentation

| Document | What's inside |
|---|---|
| [Requirements](docs/REQUIREMENTS.md) | Goal, scope, what's deliberately left out and why |
| [Design](docs/DESIGN.md) | Architecture and data model diagrams, key flows, API overview |
| [Decisions](docs/DECISIONS.md) | Trade-offs: SQLite, money as minor units, CQRS-lite, caching, sessions vs JWT, … |
| [Performance](docs/PERFORMANCE.md) | Indexes, query timings on 10k rows, bundle sizes |
| [Deployment](docs/DEPLOYMENT.md) | Docker image, CI, Render configuration and free-tier trade-offs |
| [AI workflow](docs/ai/PROMPTS.md) | How AI tools were used, phase by phase, and every mistake caught by tests or review |

## How this was built

Incrementally, one reviewed commit per step, test-first for domain logic. AI assistance (Claude Code) was used
throughout under explicit project rules ([`CLAUDE.md`](CLAUDE.md)): plan first, write tests, run them, review the diff,
then commit. Architecture rules, integration tests and live checks caught real mistakes along the way; they are listed
in the [AI workflow log](docs/ai/PROMPTS.md).

---

Built by Sachin Yadav for the Incubyte Software Craftsperson assessment.
