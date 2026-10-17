# Performance

Target from the requirements: list and dashboard responses under ~300 ms with 10,000 employees.

## How
- **Filter, sort and paginate in SQL**, never in Java. Only one page of rows leaves the database.
- **Read model in plain SQL** (`EmployeeQueryRepository`): selects only the listed columns and joins
  reference data in one statement, so no N+1 queries or entity hydration.
- **Indexes** matching the filters: `(country_code, status)`, `department_id`, `job_title`,
  `(last_name, first_name)`, `current_salary_usd_minor`, and `salary_record (employee_id, effective_date DESC)`.
- **Denormalised current salary (local + USD)** on `employee`: sorting by salary and USD aggregates are plain
  integer columns, with no "latest record per employee" subquery.
- **Batch seeding** with JDBC `batchUpdate` in one transaction.

## Measurements
MacBook (Apple Silicon), SQLite file DB, 10,000 employees, warm JVM, `curl` total time.

| Request | Matches | Time |
|---|---|---|
| Seed 10,000 employees + 18,882 salary records | — | ~230 ms |
| `GET /api/employees` (first page) | 10,000 | 7 ms |
| `?search=sharma` | 258 | 11 ms |
| `?country=IN&level=L3&status=ACTIVE` | 703 | 6 ms |
| `?sort=salaryUsd,desc&size=100` | 10,000 | 8 ms |
| `?page=399` (last page, offset 9,975) | 10,000 | 9 ms |
| `?sort=compaRatio,asc` (computed via `employee_pay` view, no index) | 10,000 | 35 ms |
| `?compaMax=0.85&sort=compaRatio` | — | 19 ms |
| `?bandPosition=BELOW&country=IN&level=L3` | — | 8 ms |
| `GET /api/pay-bands` (48 bands + headcount split) | 10,000 | 32 ms |

**Compa-ratio trade-off:** it is computed in a view, so sorting by it scans and sorts all 10k rows (~35 ms).
That's well within budget. At 100k+ rows it would be stored like `current_salary_usd_minor` and kept in sync on
salary writes and band changes.

## Insights (dashboard)
Aggregates are computed in memory from a cached snapshot of the active workforce (one lean query, ~1 MB for 10k).

| Request | Cold (snapshot load) | Cached |
|---|---|---|
| `GET /api/insights/summary` | 163 ms (first request, JIT cold) | 12 ms |
| `GET /api/insights/by-country` | — | 13 ms |
| `GET /api/insights/by-title?country=IN` | — | 9 ms |
| `GET /api/insights/distribution[?country=JP]` | — | 5 ms |
| `GET /api/insights/outliers` (all rules over 10k) | — | 17 ms |

**Why in memory:** medians need ordered data (SQLite has no `MEDIAN()`), and pure Java statistics are exact and unit
tested. **Where it stops scaling:** fine to ~100k employees; beyond that, push aggregates into SQL or maintain
summary tables updated on write.

**Cache correctness:** invalidated *after commit* by a `WorkforceChanged` event, with a generation counter so a load
that overlaps a write is never served. Verified live: doubling one salary raised total payroll by exactly that
employee's previous USD salary on the very next request.

## CSV export
| Request | Rows | Size | Time |
|---|---|---|---|
| `GET /api/employees/export.csv` (everything) | 10,000 | 1.7 MB | 171 ms |
| `…/export.csv?country=IN&title=Operations Analyst&bandPosition=BELOW` | 35 | — | 8 ms |

Rows stream from the JDBC cursor to the response (`Transfer-Encoding: chunked`), so memory stays flat regardless
of size. Trade-off: SQLite's single pooled connection is held during the export, so other requests wait ~0.2 s at worst.
