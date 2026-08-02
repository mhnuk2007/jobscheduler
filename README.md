# Job Scheduler

![Java](https://img.shields.io/badge/Java-25-blue)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Docker](https://img.shields.io/badge/Docker-containerized-blue)

A distributed job scheduler built with Spring Boot. Jobs are claimed and executed exactly once across horizontally scaled worker replicas using PostgreSQL row-level locking (`SELECT ... FOR UPDATE SKIP LOCKED`) — no external lock service, no leader election.

See [`problemstatement.md`](./problemstatement.md), [`functionalrequirements.md`](./functionalrequirements.md), and [`plan.md`](./plan.md) for the full design spec and build plan.

## Features

- One-off jobs, executed at or after a target time
- Recurring jobs on a cron schedule (6-field, Spring `CronExpression` format — seconds included)
- Exactly-once execution across concurrent worker instances
- HTTP callback execution with per-attempt outcome recording
- Exponential backoff retries
- Automatic dead-lettering after retries are exhausted, with manual replay
- Pause/resume for recurring jobs
- Full execution history, correlated by `jobId` via MDC logging
- REST API: submit, get status, get run history, cancel, pause, resume, replay
- JWT authentication (RS256) — every endpoint requires a valid bearer token; job ownership is enforced from the token's `sub` claim, scoped per-owner throughout (including idempotency keys)
- SSRF guard on callback URLs — blocks cloud metadata endpoints, loopback, link-local, and private-range addresses at both submission and execution time
- Containerized: multi-stage Docker build, runs as a non-root user, verified end-to-end (auth, scheduler loop, SSRF guard, data persistence across restarts) inside a real container

## Stack

- Java 25
- Spring Boot 4.1.0
- PostgreSQL 16
- Flyway (schema migrations)
- Spring Security (OAuth2 Resource Server, JWT)
- Lombok
- Maven
- Docker (multi-stage build, `eclipse-temurin:25-jdk`/`25-jre`)
- JUnit 5, Mockito, AssertJ, Testcontainers, WireMock, Awaitility (testing)

## Prerequisites

- JDK 25
- Docker Desktop (for local Postgres via Docker Compose, running the full app in a container, and Testcontainers-based integration tests)
- Maven (or use the included `./mvnw` wrapper)
- OpenSSL (to generate a local RSA key pair for JWT signing — see Authentication below)

## Running the App

### Option A — everything in Docker (app + Postgres)

```bash
docker compose up --build
```

Rebuilds the app image if source has changed; use `docker compose up` alone to just start existing containers. Stop with `docker compose down` (add `-v` to also wipe the Postgres volume).

### Option B — Postgres in Docker, app run directly via Maven (local dev loop)

```bash
docker compose up -d postgres
export DB_PASSWORD=0000
./mvnw spring-boot:run
```

Either way, Flyway automatically applies migrations from `src/main/resources/db/migration` on startup.

### First-time setup — generate a local RSA key pair for JWT signing

```bash
openssl genrsa -out jwt-private.pem 2048
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

`application.yml`'s `spring.security.oauth2.resourceserver.jwt.public-key-location` points at the public key; `docker-compose.yml` mounts it into the `app` container read-only. Both keys are gitignored and must be generated locally — they are never committed.

## Authentication

All API endpoints require a valid JWT (`Authorization: Bearer <token>`), verified against the local RSA public key — no external identity provider needed.

Generate a test token signed with the private key from setup (tokens expire after 1 hour, so this is a per-session step):

```bash
export TOKEN=$(python3 -c "
import jwt, time
payload = {
    'sub': 'demo-user',
    'iat': int(time.time()) - 10,
    'nbf': int(time.time()) - 10,
    'exp': int(time.time()) + 3600
}
print(jwt.encode(payload, open('jwt-private.pem', 'rb').read(), algorithm='RS256'))
")
```

The `sub` claim becomes the job's owner — only requests bearing a token with the matching `sub` can view, cancel, pause, resume, or replay a job. A different `sub` (or no token at all) gets `404`/`401` respectively; cross-owner access is deliberately indistinguishable from "job doesn't exist," to avoid leaking which job IDs are in use. Idempotency keys are also scoped per owner — two different users may reuse the same key independently.

Quick sanity check the app is up and auth is working:
```bash
curl -i http://localhost:8080/api/v1/jobs/nonexistent -H "Authorization: Bearer $TOKEN"
```
Expect `404` (authenticated, job doesn't exist).

## Submitting a Job

One-off:
```bash
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "type": "ONE_OFF",
    "runAt": "2026-08-01T12:00:00Z",
    "callback": { "url": "https://httpbin.org/post", "method": "POST" },
    "maxRetries": 3,
    "timeoutSeconds": 30
  }'
```

Recurring (note: 6-field cron, seconds first — a 5-field Unix-style expression is rejected):
```bash
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "type": "RECURRING",
    "cronExpression": "0 */1 * * * *",
    "callback": { "url": "https://httpbin.org/post", "method": "POST" }
  }'
```

Then check status and run history:

```bash
curl http://localhost:8080/api/v1/jobs/<jobId> -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/v1/jobs/<jobId>/runs -H "Authorization: Bearer $TOKEN"
```

Lifecycle actions:
```bash
curl -X POST http://localhost:8080/api/v1/jobs/<jobId>/pause   -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/api/v1/jobs/<jobId>/resume  -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/api/v1/jobs/<jobId>/replay  -H "Authorization: Bearer $TOKEN"
curl -X DELETE http://localhost:8080/api/v1/jobs/<jobId>       -H "Authorization: Bearer $TOKEN"
```

`pause`/`resume` are only valid for `RECURRING` jobs; `replay` is only valid for a `DEAD_LETTER` job.

## Running Tests

```bash
./mvnw test
```

The suite spans three layers:

- **Unit tests** (`JobServiceImplTest`, `ExponentialBackoffPolicyTest`, `CronEvaluatorImplTest`) — pure Mockito/JUnit, no Spring context, no database. Cover business rules directly: ownership isolation, type guards, idempotency scoping, cron validation, SSRF rejection, backoff math.
- **Web-layer slice tests** (`JobControllerTest`) — `@WebMvcTest` with `JobService`/`JobMapper` mocked and a real Spring Security filter chain (`@EnableWebSecurity` + mocked `JwtDecoder`). Cover HTTP status codes, JSON shape, and JWT/authorization wiring in isolation from business logic.
- **Integration tests** (`JobClaimerImplConcurrencyTest`, `RetryToDeadLetterIntegrationTest`, `ReplayIntegrationTest`) — full Spring context against a real Testcontainers Postgres. Callback execution is driven through a local WireMock server rather than a real external endpoint, so these tests are deterministic and never depend on an external service being up.

Integration tests require a running Docker daemon.

### Windows / Docker Desktop notes

If Testcontainers fails to detect a valid Docker environment, this is typically a named-pipe resolution issue on Windows. Enable TCP access in Docker Desktop (**Settings → General → "Expose daemon on tcp://localhost:2375 without TLS"**), then create `~/.testcontainers.properties`:

```properties
docker.host=tcp://localhost:2375
```

## Project Structure

```
src/main/java/dev/mhnuk2007/jobscheduler/
├── job/
│   ├── domain/       # Job, JobRun, Callback entities + enums
│   ├── api/          # JobController, DTOs
│   ├── repository/   # Spring Data repositories, JSONB converters
│   ├── service/      # JobService — submission, ownership-scoped lifecycle
│   └── exception/    # Domain exceptions + GlobalExceptionHandler
├── scheduling/       # JobClaimer (locking), JobPoller, CronEvaluator
├── execution/        # JobExecutor, HttpCallbackExecutor, RetryPolicy, DeadLetterHandler
├── security/         # CallbackUrlValidator (SSRF guard)
└── config/           # SecurityConfig (JWT), SchedulerConfig (poller thread pool)

Dockerfile            # Multi-stage build: eclipse-temurin 25-jdk (build) -> 25-jre (runtime)
docker-compose.yml    # postgres + app services
```

## Verified

Proven with both automated tests and real, manual end-to-end sessions against a running instance — including a full pass inside a real Docker container, not just locally via Maven:

- Concurrent job claiming: 8 workers racing over 100 due jobs, zero duplicate claims
- One-off job success path: claim → HTTP 200 → job marked complete
- One-off job failure path: claim → HTTP 500 → exponential backoff → retry → `DEAD_LETTER` — covered by an automated integration test, proven manually locally, and reproduced inside the container
- Accurate HTTP status codes recorded on both success and failure
- Live polling: newly inserted jobs are picked up without restarting the app
- Full REST API: submit, get, get-runs, cancel, pause, resume, replay — via HTTP, not raw SQL
- Idempotency: resubmitting with the same key returns the original job; scoped per-owner, so two different users may reuse the same key independently
- JWT authentication: valid signed tokens accepted; missing/invalid tokens rejected with `401` — confirmed both locally and inside the container
- Ownership isolation: a job is only visible/actionable by its creator's token `sub` — verified on every endpoint, including pause/resume/replay, and re-confirmed inside the container; cross-owner access returns `404`, indistinguishable from a nonexistent job
- SSRF guard: callback URLs targeting the cloud metadata address (`169.254.169.254`), loopback, and private IP ranges are rejected with `400` at submission time; legitimate external URLs are unaffected — confirmed inside the container as well
- Recurring jobs: submitted, correctly reschedule `nextRunAt` on each successful firing, and remain in `SCHEDULED` (never a terminal state) between cycles
- Pause: verified to actually halt claiming, not just update a status label
- Resume: verified to restart claiming
- Replay: verified via run history — a dead-lettered job is genuinely re-claimed and re-executed
- Type guards: pause/resume correctly reject `ONE_OFF` jobs with `409`
- Containerization: the app builds and runs correctly in Docker, connects to a containerized Postgres via internal service networking, and Postgres data survives an app container restart
- Full automated test suite passing: 43 tests across unit, slice, and integration layers

## Roadmap

- Prometheus metrics
- Kubernetes deployment, Terraform infrastructure

## Database

Schema is owned by Flyway (`src/main/resources/db/migration`), not Hibernate — `spring.jpa.hibernate.ddl-auto` is set to `none`.

Default local connection:
```
jdbc:postgresql://localhost:5433/job_scheduler
```