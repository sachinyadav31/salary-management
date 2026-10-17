# Design

Pay Atlas is a **modular monolith**: one Spring Boot application that serves a REST API and the Angular single-page
app from the same origin, backed by SQLite. Features are packages with enforced boundaries (ArchUnit), so the
system is simple to run and still easy to split later.

## System overview

```mermaid
flowchart LR
    subgraph Browser
        SPA["Angular 20 SPA<br/>(signals, Material, ECharts)"]
    end

    subgraph App["Spring Boot 4 (single Docker image)"]
        SEC["Security filter chain<br/>session cookie · CSRF · 401/403 problem JSON"]
        STATIC["Static SPA + fallback<br/>(hashed assets cached 1 year)"]
        API["REST controllers (/api)"]
        CMD["Write side<br/>EmployeeService · SalaryService<br/>(JPA entities, domain rules)"]
        QRY["Read side<br/>SQL query repositories<br/>(directory, pay bands, CSV stream)"]
        INS["Insights<br/>snapshot cache + pure calculators"]
        JOB["SalaryRolloverJob<br/>(startup + daily)"]
        EVT(("Domain events<br/>EmployeeHired · WorkforceChanged"))
    end

    DB[("SQLite<br/>Flyway migrations V1–V6<br/>employee_pay view")]

    SPA -- "HTTPS, same origin" --> SEC
    SEC --> STATIC
    SEC --> API
    API --> CMD
    API --> QRY
    API --> INS
    CMD --> DB
    QRY --> DB
    INS -- "one lean query, cached" --> DB
    JOB --> CMD
    CMD -- publish --> EVT
    EVT -- "HIRE record (same transaction)" --> CMD
    EVT -- "invalidate after commit" --> INS
```

**Request path:** every request passes the security filter chain. `/api/**` needs a session; the SPA shell, static
assets, health check and API docs are public. Controllers never touch JPA repositories (an ArchUnit rule): writes go
through services; reads use SQL query repositories that select only the needed columns.

## Packages (by feature)

| Package | Responsibility |
|---|---|
| `employee` | Employee aggregate, create/update/deactivate, directory read model, CSV export |
| `salary` | Effective-dated, append-only salary history; rules in pure `SalaryHistory`; rollover job |
| `payband` | Pay bands per country × level, compa-ratio and band position (SQL view `employee_pay`) |
| `insights` | Workforce snapshot cache, `PayStatistics`, `Histogram`, `InsightsCalculator`, `OutlierDetector` |
| `reference` | Countries, departments, levels, fixed FX rates, `CurrencyConverter` |
| `auth` | Users, session login, lockout, `CurrentUser` for audit, security configuration |
| `meta` | Composes reference data for the UI's filters (sits above the features it reads) |
| `seed` | Deterministic 10k-employee generator and JDBC batch seeder |
| `common` | `Money`, problem-detail errors, CSV writer, SPA serving, OpenAPI |

Each feature has an `api` sub-package for HTTP. Enforced rules (`ArchitectureTest`): no cycles between features,
domain classes free of Spring/JPA, no entities or repositories in `api`, nothing depends on `api`, no `double`/`float`
fields outside seed data.

## Data model

```mermaid
erDiagram
    COUNTRY ||--o{ EMPLOYEE : "employs"
    DEPARTMENT ||--o{ EMPLOYEE : "contains"
    EMPLOYEE ||--|{ SALARY_RECORD : "has history"
    COUNTRY ||--|{ PAY_BAND : "defines"
    COUNTRY }o--|| FX_RATE : "currency rate"
    APP_USER |o--o{ SALARY_RECORD : "recorded by"
    APP_USER |o--o{ EMPLOYEE : "last edited by"

    EMPLOYEE {
        int id PK
        text employee_code UK
        text email UK
        text country_code FK
        int department_id FK
        text job_title
        text level "L1-L6"
        text hire_date "ISO date"
        text status "ACTIVE | INACTIVE"
        int current_salary_minor "denormalised, local currency"
        text current_salary_currency
        int current_salary_usd_minor "denormalised, for sort and USD aggregates"
        int version "optimistic locking"
        int updated_by_user_id FK
    }
    SALARY_RECORD {
        int id PK
        int employee_id FK
        int amount_minor "CHECK > 0"
        text currency_code
        text effective_date
        text reason "HIRE | PROMOTION | ANNUAL_REVIEW | MARKET_ADJUSTMENT | CORRECTION"
        text note
        int recorded_by_user_id FK
    }
    PAY_BAND {
        text country_code PK
        text level PK
        int min_minor
        int mid_minor
        int max_minor
    }
    FX_RATE {
        text currency_code PK
        text usd_per_unit "decimal string"
        text as_of_date
    }
    APP_USER {
        int id PK
        text email UK
        text full_name
        text password_hash "BCrypt"
    }
```

- **Money** is always integer minor units plus an ISO currency (JPY has 0 decimals). Never floating point.
- **Dates** are ISO text, so they sort and compare correctly in SQLite.
- `employee_pay` (view) computes **compa-ratio** in basis points and **band position** once, for search, sort,
  filters and insights alike.
- `current_salary_minor` / `current_salary_usd_minor` duplicate the latest effective salary record. They are a cache,
  kept in sync in the same transaction as every salary change and by the rollover job for scheduled changes.

## Key flows

### Recording a salary change

```mermaid
sequenceDiagram
    actor HR as HR manager
    participant UI as Angular drawer
    participant API as SalaryController
    participant S as SalaryService
    participant H as SalaryHistory (pure)
    participant DB as SQLite
    participant C as Insights cache

    HR->>UI: amount, date, reason (live preview: % change, compa-ratio)
    UI->>API: POST /salary-changes {amount "88000.00", version 3} + X-XSRF-TOKEN
    API->>S: recordChange(cmd)
    S->>DB: load employee, check version == 3 (else 409)
    S->>H: checkCanAdd(change, today)  (not before hire, ≤ 1 year ahead, corrections need a note)
    S->>DB: insert salary_record (recorded_by = signed-in user)
    alt effective today or earlier
        S->>DB: update current salary (local + USD), version → 4
    else scheduled
        S->>DB: force version → 4 (rollover applies it on the date)
    end
    S-->>C: WorkforceChanged (after commit)
    API-->>UI: 201 {employee v4, history}
    Note over UI: On 409: load latest, show what changed,<br/>keep the user's input, let them save again
```

### Signing in

```mermaid
sequenceDiagram
    participant UI as Angular
    participant SEC as Spring Security
    participant AUTH as AuthController

    UI->>SEC: GET /api/auth/session (app start)
    SEC-->>UI: {authenticated:false} + Set-Cookie XSRF-TOKEN (readable)
    UI->>AUTH: POST /api/auth/login {email, password} + X-XSRF-TOKEN
    AUTH->>AUTH: lockout check → BCrypt verify → new session id (fixation)
    AUTH-->>UI: 200 {fullName} + Set-Cookie JSESSIONID (HttpOnly, Secure)
    UI->>SEC: GET /api/... (cookie sent by the browser)
    Note over UI,SEC: Any later 401 → "session expired" → sign in → back to the same page
```

## API

Interactive documentation: `/swagger-ui.html` (spec at `/v3/api-docs`).

| Area | Endpoints |
|---|---|
| Auth | `GET /api/auth/session`, `POST /api/auth/login`, `POST /api/auth/logout` |
| Employees | `GET /api/employees` (search, filters, sort, paging), `GET /api/employees/export.csv`, `GET/POST /api/employees`, `PUT /api/employees/{id}`, `PATCH /api/employees/{id}/status` |
| Salaries | `GET /api/employees/{id}/salary-history`, `POST /api/employees/{id}/salary-changes` |
| Insights | `GET /api/insights/summary`, `/by-country`, `/by-title?country=`, `/distribution`, `/outliers` |
| Reference | `GET /api/meta`, `GET /api/pay-bands` |

Conventions: errors are RFC 7807 problem details (`400` with field errors, `401`, `403`, `404`, `409`, `429`); money
is `{minorUnits, currency, amount}`; writes that change an employee carry the `version` that was read.

## Frontend

- **Feature folders**: `employees` (directory, drawer, forms), `insights` (dashboard, chart options), `auth` (login);
  `core` (API clients, auth, interceptors) and `shared` (money formatting, band bar, charts, dialogs).
- **The URL is the state**: directory filters, sort, page, the open employee and the dashboard's selected country
  live in the URL, so every view can be bookmarked and shared, and Back works.
- **Logic in pure functions** (query mapping, money parsing and formatting, chart options, salary preview), unit
  tested with Vitest; components stay thin.
- **One error policy**: interceptors turn problem details into toasts, except where a form shows them inline and
  for 401, which means "sign in again".
