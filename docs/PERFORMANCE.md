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
