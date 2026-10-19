# Deployment

One Docker image serves both the API and the Angular app from the same origin (no CORS), deployed to Render.

## Pipeline

```
push to main ──► GitHub Actions CI ──► Render (auto-deploy after CI checks pass)
                 ├─ backend: ~210 tests (unit, slice, integration, ArchUnit)
                 ├─ frontend: Vitest + production build (bundle budgets)
                 └─ docker: the image Render will build
```

## Image (`Dockerfile`)

| Stage | Base | Does |
|---|---|---|
| frontend | `node:22-alpine` | `npm ci`, `ng build --configuration production` |
| backend | `gradle:9.8.1-jdk21` (pinned to the wrapper version) | `bootJar` with the Angular build in `static/` |
| runtime | `eclipse-temurin:21-jre-alpine` | runs the jar as a non-root user; SQLite in `/data` |

- Dependency manifests are copied before sources, so code changes reuse the npm/Maven layers.
- Tests don't run inside the image build; CI runs them first.
- JVM flags: `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC`, sized from the container's memory limit.

### Measured locally with Render free-tier limits (`docker run -m 512m --cpus 0.5`)

| | |
|---|---|
| Image size | 438 MB (mostly the JRE) |
| Ready (Spring Boot start + seeding 10k employees) | ~40 s at 0.5 CPU (Render free has 0.1 CPU, so expect 2–3 min) |
| Seeding 10,000 employees + 54,907 salary records + 16,038 bonuses | ~1 s |
| Memory after startup | 257 MB of 512 MB |

## Render service settings

| Setting | Value | Why |
|---|---|---|
| Language | Docker | Builds the `Dockerfile` at the repo root |
| Region | Singapore | Closest to the users (India) |
| Instance | Free | Demo |
| Health check path | `/actuator/health` | Traffic is routed only after startup and seeding |
| Environment variables | none | The image sets `SALARY_DB_PATH`, `SEED_ENABLED`, `SESSION_COOKIE_SECURE=true`, JVM options; Render injects `PORT` |
| Auto-Deploy | After CI checks pass | A failing test never reaches the live site |
| Build filters (ignored) | `docs/**`, `**/*.md` | Documentation-only commits don't redeploy |

## Free-tier trade-offs (deliberate for a demo)

- **Sleeps after ~15 min idle.** The first request afterwards wakes the service (JVM start on 0.1 CPU).
  A scheduled GitHub Action (`keep-warm.yml`) asks for a ping of `/actuator/health` every 10 minutes, but GitHub
  runs schedules best effort: on this repo the runs started 2-4 hours apart, so the app still sleeps between them.
  For a reliably awake demo, point a free external uptime monitor (e.g. UptimeRobot or cron-job.org, every 5-10
  minutes) at `/actuator/health`, or use a paid instance, which never sleeps. Either way, open the link once before
  a demo.
- **Ephemeral disk.** The SQLite file is lost on every deploy or restart. Seed-on-startup brings back the same
  deterministic 10,000 employees, so the demo always works, but edits made in the demo don't persist.
- **Production path:** a persistent disk (paid plan) for SQLite, or Postgres for multiple instances. Flyway
  migrations already own the schema, so moving databases means swapping the driver and dialect, not the code.

## Sessions on the free tier

Sessions are held in memory. A deploy, a restart or the free tier waking from sleep signs everyone out; the app
answers with "Your session expired" and returns the user to the same page after signing in. Behind Render's TLS the
app reads `X-Forwarded-*` headers (`server.forward-headers-strategy=framework`), so the session cookie is `Secure`.

## Run the production image locally

```bash
docker build -t pay-atlas .
```

```bash
docker run --rm -p 8080:8080 -m 512m -e SESSION_COOKIE_SECURE=false pay-atlas
```

Then open http://localhost:8080 (app) and http://localhost:8080/swagger-ui.html (API docs), and sign in with a demo
account from the README.
