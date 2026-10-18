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

### Phase 10a — Insights and caching
- Chose in-memory computation over SQL window functions for medians: pure, exact, unit-testable classes
  (`PayStatistics`, `Histogram` with 1/2/5×10ⁿ "nice" buckets, `InsightsCalculator`), with the scaling limit
  documented.
- Cache invalidation is `@TransactionalEventListener(AFTER_COMMIT)`, the deliberate opposite of phase 8's synchronous
  HIRE listener. A generation counter closes the "load overlaps a write" race; a deterministic unit test reproduces it.
- Corrected my own test: it expected two values of 9×10¹⁸ to sum without overflow. The real contract is "realistic
  payrolls fit easily; overflow fails loudly instead of wrapping", so the test now asserts `ArithmeticException`.
- Integration tests with real commits: a committed hire appears on the next read; a rolled-back hire leaves the
  same cached snapshot in place.

### Phase 10b — Outlier spotlight
- Refined the plan before coding: measure every rule in compa-ratio points (1 point = 0.01), so
  severity = people × points is comparable across rule types. The originally planned "max/min salary ≥ 1.6×" spread
  rule became "compa range ≥ 0.50" (same band, so equivalent, but in the shared unit).
- Department lag is org-wide (one card per department) to avoid eight near-identical per-country cards.
- Guards: minimum group size 5 (noise and privacy), sorted group keys and an id tie-break for deterministic output.
- Verified on 10k: the top underpaid card ("35 of 123 Operations Analysts in India") links to a filter that returns
  exactly 35 people. The spotlight also exposes a real policy issue in the data: country × level bands don't fit
  every job family (Engineering clusters above band, Operations below). That makes job-family bands the natural
  next iteration.

### Phase 11a — CSV export
- Same filters and sort as the directory (shared `EmployeeFilterParams` record + shared WHERE builder), so the
  export always matches what's on screen.
- Security: text cells starting with `= + - @`, tab or CR get a `'` prefix (OWASP CSV injection); number cells are
  not guarded, since negative numbers are legitimate. UTF-8 BOM so Excel shows non-ASCII names correctly.
- Refactor during the phase: search and export had 10 duplicated `@RequestParam`s; replaced them with one
  record bound from the query string.
- Verified: 10k rows / 1.7 MB in 171 ms, chunked (streamed); the filtered export of a spotlight card returns exactly
  the card's 35 people.

### Phase 11b — OpenAPI / Swagger UI
- springdoc-openapi 3.1.1 (built against Spring Boot 4.1; checked the POM before adding it).
- API-level description states the conventions once (exact money, USD reporting, RFC 7807, optimistic locking);
  each controller gets a `@Tag`, and each endpoint a one-line `@Operation`.
- `@ParameterObject` on the shared filter record, otherwise Swagger would show the directory filters as one opaque
  object; a test asserts `country`, `level`, `bandPosition`, … appear as individual query parameters.
- Opened `/swagger-ui.html` in the built-in browser to confirm it renders.

### Phase 11c — Architecture tests (ArchUnit)
- Seven executable rules: no feature cycles, framework-free domain classes, controllers never use JPA repositories
  or `EntityManager`, no entities in the API layer, nothing depends on `api`, controllers live in `api`,
  no `double`/`float` fields outside seed data.
- Wrote the rules first and let them fail. They found **three real issues in code written earlier in this session**:
  1. Cycle `employee ↔ payband`: `JobLevel` lived in `employee` but is reference data used by bands. Moved it to
     `reference`.
  2. Cycle `employee ↔ reference`: `MetaController` composed data from both but lived in `reference`. Moved it to a
     new top-level `meta` feature.
  3. (Not predicted) `InsightsController` called JPA repositories to validate filters. Added `ReferenceDataService`.
- Lesson for the AI workflow: plausible-looking generated code still drifts architecturally; executable rules catch
  that drift on every build, not just at code review.

### Phase 12a — UI shell and theme ("Pay Atlas")
- Dark-first M3 theme: every colour is a CSS `light-dark()` pair, so switching `color-scheme` flips the whole app
  (Material included) without duplicate theme CSS. Band colours (coral below / teal within / violet above) are
  global tokens so they mean the same thing on every screen.
- Fonts (Inter, Space Grotesk) and Material Symbols are self-hosted via npm instead of Google CDNs: no third-party
  requests, works offline.
- Typed API models mirror the backend DTOs; one HTTP interceptor turns RFC 7807 problem details into snackbars.
- Theme choice is persisted, with storage access wrapped in try/catch (private mode can't break the app).
- Verified in the built-in browser with the backend running behind the dev proxy (FX date comes from `/api/meta`).
- Small test fix: asserted on `.label` text, not the icon ligature + label `textContent`.

### Phases 12b–12c — Shared UI and employee directory
- Every visual piece is a pure function plus a thin component: `formatMoney` (per-currency locale, e.g. lakh grouping
  for INR), `countryFlag`, `bandBarGeometry` (band plus 25% padding so outliers sit visibly outside). All are unit
  tested, and screen-reader labels describe what the colour shows.
- The URL is the state: filters/sort/page live in query params, parsed tolerantly. Backend filter names were reused
  as URL param names, so outlier cards deep-link with zero mapping code (tested).
- `switchMap` cancels stale requests; the previous page stays visible (dimmed) while the next loads; the search box
  is debounced and uses `replaceUrl` so typing doesn't flood history.
- Verified in the built-in browser on the 10k dataset: quick filter + sort update the URL; the outlier deep link
  returns exactly 35 people like the card; no horizontal page scroll on a 375 px phone. Fixed wrapped action buttons
  on phones found during that check.
- Production build exceeded the 500 kB initial budget by 14 kB (131 kB transferred); raised the warning budget with a
  documented reason rather than contorting the code.

### Phase 13a — Chart foundations
- ECharts 6 imported modularly (only bar/scatter/effect-scatter + geo/grid/tooltip + canvas) behind a ~50-line
  `<pa-chart>` wrapper instead of `ngx-echarts`: no wrapper version to track, runs outside Angular's zone (mouse-move
  over a canvas doesn't trigger change detection), resizes with its container, disposes on destroy.
- Canvas needs concrete colours, but `light-dark()` custom properties read back unresolved; a probe element resolves
  each token for the active theme.
- World outlines: world-atlas 110m TopoJSON (ISC) served as a static asset and converted at runtime, not bundled JS.
  Bubbles at fixed coordinates also show Singapore, which is too small for the 110m outlines.
- Pure option builders, tested: bubble *area* ∝ payroll (sqrt scale), colour = band health (healthy ≥ 87% within,
  watch ≥ 85%, else concern), selected country moves to a rippling series.
- Tests caught two issues: compact currency formatting showed "₹10.0L"/"$10.0K" (currency style forces a minimum
  of 2 decimals; set `minimumFractionDigits: 0`), and an arbitrary tie-break in the percentage split (now explicit).
- Also fixed my own wrapper bug before it ran: a stray `inject()` inside an `afterNextRender` callback (outside the
  injection context).

### Phase 13b — Insights dashboard
- Each card loads, fails and retries independently (`loadable()` helper); KPI numbers count up outside Angular's zone
  and skip the animation when reduce-motion is on. The country selection lives in the URL, like the directory filters.
- Verified in the built-in browser on 10k employees: KPIs, payroll bubble map, spotlight cards, countries table and
  an INR histogram for India after clicking its bubble.
- Issues found while checking and fixed:
  1. A runtime bug the compiler couldn't catch: `inject()` inside a lambda run later by `switchMap` (outside the
     injection context). Moved it to a field before running.
  2. Horizontal stripes across the map: polygons crossing the ±180° meridian (Russia, Fiji). Unwrapped those rings
     (+360° on the western half) and dropped Antarctica; covered by a unit test.
  3. "Map failed" on first load turned out to be a stale dev-server cache after installing ECharts mid-session, not
     app code. A restart fixed it.
  4. One click of mine landed on the wrong element because I used a stale screenshot. I reproduced the interaction
     with synthetic events before concluding the app was fine.
- Component tests stub the canvas chart (jsdom has no canvas) and cover KPIs, spotlight links (exact filter),
  country selection → URL → INR distribution request, and isolated card failure.

### Phase 14a–14b — Employee drawer (read view)
- Drawer is a child route (`/employees/:id?…filters`): deep-linkable, Back closes it, and the list's query params
  don't change, so opening it doesn't refetch the list (asserted in a test).
- Forms will be presentational; the drawer owns API calls and conflict handling (one place for 409 logic). Writes
  carry an `HttpContext` flag so the global snackbar stays quiet for errors a form shows inline.
- `parseAmount` uses string arithmetic (a test pins `0.29` → 29 minor units, which `0.29 * 100` gets wrong), and the
  salary preview uses the backend's exact compa-ratio integer formula.
- Found while checking in the browser:
  1. The directory's sticky table header (Material z-index 100) drew over the drawer; the drawer is now the modal
     layer (z-index 1000).
  2. The timeline chart was blank: modular ECharts didn't register `LineChart` and only logs a console error. Added a
     test that checks every series type the option builders use against **ECharts' own registry**, so this class of
     bug fails CI instead of failing silently.
  3. Single-record histories drew nothing; the solid line now carries the current salary forward to today.
  4. Duplicate y-axis labels (¥6.6M twice); axis ticks now use 3 significant digits.

### Phase 14c — Edit, salary change, deactivate, conflicts
- Presentational forms (salary change, profile) emit requests; the drawer submits and handles every error class:
  400 → messages on the matching fields, 409 → conflict flow, other → form-level message.
- Conflict flow: on 409 the drawer immediately loads the latest version, shows *what changed* (field diff), and keeps
  the user's form open with their input, so the next save carries the new version and nothing is lost or silently
  overwritten. Verified live by editing the same employee with `curl` while the form was open: the colleague's
  L2 → L3 change moved the employee into a new band, and the same typed ¥7.2M re-previewed from "above band" to
  "compa 0.94, within band". That's exactly why re-saving must be a deliberate step.
- Live salary preview (% change, compa-ratio, band position) uses the backend's integer formula; amounts are parsed
  with string arithmetic.
- Deactivation asks for confirmation; reactivation doesn't. A reusable `canLeaveGuard` asks before closing with
  unsaved input (X, backdrop, Escape, Back). It lives in its own file so the route config doesn't pull the lazy
  drawer into the main bundle.
- Caught before running: validators reading a required signal input during FormControl construction (would throw
  NG0950). They read a snapshot synced by an effect instead.
- Found while checking in the browser: a field hint crowding the next row's floating label in the narrow drawer
  (added row spacing). A test-setup gap (router input binding missing in the test, unlike the app) was fixed in the test.

### Phase 15 — Docker, CI, Render
- Spring Boot serves the Angular build (SPA fallback for deep links; unknown `/api/**` and missing files stay 404;
  hashed bundles cached for a year, `index.html` revalidated; gzip).
- Multi-stage Dockerfile; the backend stage uses the official `gradle:9.8.1-jdk21` image pinned to the wrapper's
  version, because the JVM on this machine's network couldn't download the Gradle distribution (same issue as day one).
  Verified under Render's limits before pushing: ready in ~40 s at 0.5 CPU, 257 of 512 MB.
- CI (GitHub Actions): backend tests, frontend tests + production build, Docker image build. Green on first run.
- **Bug found only on the live site:** all fonts under `/media/` returned 404, so icons showed as text ("grc", "lig").
  Cause: for a `/media/**` handler Spring resolves only the `**` part against the location, so it looked in
  `static/` instead of `static/media/`. My test covered a `.js` bundle but no font, and my local Docker smoke test
  didn't request one. Fixed, added a font regression test, and re-verified by fetching the real fonts from a
  rebuilt image. Lesson: smoke-test every asset type the page actually loads, not just the HTML and the API.

### Phase 16a — Light, Incubyte-inspired theme
- Studied incubyte.co (brand extraction + browsing): forest green #014D43, lime #CBDF7A, navy text, Inclusive Sans,
  serif display headlines, pill buttons, calm light surfaces.
- Because every colour was already a token, the re-theme was mostly new token values: Material theme overrides,
  pill button shapes, Fraunces (open-source serif) for headings, Inclusive Sans for UI, Inter kept for tabular
  numbers. The user chose light-only, so the theme service, toggle and light/dark pairs were removed (no dead code).
- Deliberately not used: Incubyte's logo or name inside the product. The brief's client is ACME, so the app stays
  "Pay Atlas", with a small credit line for the assessment.

### Phase 16b — Backend authentication and audit
- Chose server sessions over JWT for a same-origin SPA: HttpOnly session cookie (unreadable by XSS), real
  server-side logout, Spring Security 7's built-in `csrf.spa()` (XSRF-TOKEN cookie, echoed as a header).
  401/403 are problem JSON, never an HTML redirect.
- Hardening: one generic message for wrong password / unknown email (identical bodies, tested; Spring also hashes a
  dummy password for unknown users), lockout after 5 failures with `Retry-After`, new session id on sign-in,
  30-minute idle timeout, Secure cookie behind Render's TLS.
- Audit: salary records store who recorded them, employees who last edited them; system work (seed, rollover)
  records nobody. Services get the actor from a small `CurrentUser` component, so domain classes stay framework-free.
- Tests caught:
  1. `Retry-After` reported 299 s instead of 300 s (truncation); now rounds up so a client never retries while
     still locked.
  2. spring-security-test's `csrf()` helper swaps the shared CSRF repository for one that never writes the cookie,
     which broke a later test in the cached context. The integration test now uses the real cookie-and-header flow
     for every write, which is also closer to what the browser does.
- Single persona per the brief: every signed-in user is an HR admin; a read-only role would be a small addition.

### Phase 16c — Frontend authentication
- `AuthService` mirrors who is signed in; the session itself is an HttpOnly cookie JavaScript never sees (verified in
  the browser: `document.cookie` has XSRF-TOKEN but not JSESSIONID).
- An app initializer loads the session before routing, which also issues the CSRF cookie. Guards send signed-out
  visitors to `/login?returnUrl=…`, and a 401 anywhere means "session expired", so the user signs in and returns.
- Avoided a subtle trap: the shell's shared, cached `/api/meta` request would fail with 401 before login and stay
  failed, so it's only requested once signed in.
- `returnUrl` only accepts same-app paths (an open-redirect test covers `//evil.example`).
- Verified in the browser: deep link → login → back to the drawer; a salary change from the UI recorded
  "by Priya Sharma"; sign-out kills the server session (API 401, Back button guarded); restarting the backend while
  signed in leads to "Your session expired" with no error toasts.
- Verified on the live site after CI: deep link → sign-in → back to the drawer; the CSRF cookie arrives `Secure`
  (forwarded headers behind Render's TLS work); a salary change over HTTPS recorded "by Priya Sharma"; the session
  cookie is invisible to JavaScript.

### Phase 17 — Documentation
- README (live link, demo accounts, run/test/deploy), DESIGN (Mermaid system, data model and flow diagrams) and
  DECISIONS (19 decisions with alternatives and trade-offs), written after the code so they describe what exists.
- Caught while writing: the image's `Secure` session cookie may not be stored by Safari on plain-HTTP localhost, so
  the local `docker run` instructions pass `SESSION_COOKIE_SECURE=false`.

### Phase 18 — Review feedback: realistic pay cycles
- Reviewing the app as the HR user surfaced that seeded raises fell on random dates. Real companies review pay once a
  year, so the seed now uses a **1 April review cycle** (10 cycles since 2017, ~85% get a raise, new joiners wait at
  least 6 months), with promotions and market adjustments off-cycle and performance bonuses paid on 1 April.
- Requirements updated first (v0.4): career history, variable pay and bonuses, and bulk changes in scope; payroll runs
  (actually paying 10,000 people on payday) explicitly out of scope.

### Phase 19 — Career history
- Profile edits now record **career events** (level, title, department, variable-target changes, deactivation and
  reactivation) computed by a pure `CareerChanges.between(before, after)`; the drawer merges salary changes, career
  events and bonuses into one timeline. A level change offers "Record raise" with the reason pre-selected.
- Tests caught:
  1. With a fixed test clock every event shares one instant, so "newest first" came out reversed; ordering is now
     `occurred_at desc, id asc` and the test asserts recording order.
  2. Setting `editedBy` *after* `save` dirtied the brand-new entity and bumped it to version 1, so the next edit with
     version 0 got a 409 in the integration test. Now everything is set before the first save.
- Process slip: one commit went in with a failing test (a `;` instead of `&&` in the command chain). Caught on the
  next run, fixed, and amended before pushing.

### Phase 20 — Merit raises, variable pay and bonuses
- Prompted by "some employees get a bonus and a raise if the owner is happy with their work" and "consider variable
  pay". Designed as three separate things (DECISIONS 20–21): a MERIT reason, a variable-pay target per person, and an
  append-only bonus ledger, with totals (target cash, bonuses in the last 12 months) on the dashboard.

### Phase 21 — Bulk raises and bonuses
- Prompted by "do I have to handle the salaries of 10,000 employees on the same day, or bulk things?". Answer: paying
  salaries is payroll (out of scope); applying a raise or bonus to a group is HR's job, so it's in.
- Design (DECISIONS 22): preview → apply in one transaction; a pure planner that reuses the single-change rules;
  skipped people grouped by reason; idempotent request id; the preview is never trusted at apply time.
- Verified in the browser: India · Engineering · L3 (239 people) +6% → +$257.2K a year; running it again skipped all
  239 ("a change already exists on that date"). Whole company: apply 9,708 raises in ~0.3 s, duplicate submit 8 ms.
- UX fixes found by using it, not by tests: the Preview button sat below the fold on a laptop-height dialog (moved to
  the action bar) and focus landed on the toggle instead of the % field.
- **Caught while updating the docs:** the local dashboard showed $0 variable pay and $0 bonuses. The local SQLite
  file predated the new seed (the seeder skips a non-empty database), so it never got the new data. Production is
  unaffected (fresh disk on every deploy), but it was a reminder to re-measure demo numbers from a clean database.

### Phase 22 — Missing basics: add employee and ⌘K
- Prompted by "how do we add employees? basic is missing — can you think of more missing basics". Checked the app
  against its own requirements: "create" was promised and the API had it, but there was no screen; the ⌘K palette
  promised under Experience didn't exist either. Pay bands can't be viewed or edited yet (proposed, not built).
- Add employee: the country fixes the currency, the server assigns the code, and the salary is previewed against the
  pay band before saving. While reading the create path I found nothing limited the hire date (1900 or 2062 were
  accepted), so a small pure rule now allows 1950 to one year ahead, mirrored in the form.
- ⌘K: people from the existing directory search, commands as plain links, combobox/listbox ARIA.
- **Caught by the production build, not by tests:** the first palette commit grew the initial bundle from 567 kB to
  788 kB because the app shell imported it (and the add-employee form behind it) eagerly. Fixed by loading the palette
  on first use and resolving MatDialog inside that chunk: back to 570 kB. The bundle budget did its job as a tripwire;
  the lesson is to run the production build before committing UI changes, not only after.

### Phase 23 — Keep the Render demo awake
- Prompted by "add github workflow to keep render warm". A scheduled workflow pings the public `/actuator/health`
  every 10 minutes (free tier sleeps after ~15), with retries long enough to ride out a cold start, plus a manual
  trigger. No permissions, no secrets; the URL can be overridden with the `RENDER_APP_URL` repository variable.
- Limits written down rather than hidden: GitHub delays schedules under load and pauses them after 60 days without
  repository activity, and one always-on service uses ~744 of the free tier's 750 instance hours a month.
