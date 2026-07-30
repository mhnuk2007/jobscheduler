# Job Scheduler

![Java](https://img.shields.io/badge/Java-25-blue)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)

A distributed job scheduler built with Spring Boot. Jobs are claimed and executed exactly once across horizontally scaled worker replicas using PostgreSQL row-level locking (`SELECT ... FOR UPDATE SKIP LOCKED`) — no external lock service, no leader election.

See [`problemstatement.md`](./problemstatement.md), [`functionalrequirements.md`](./functionalrequirements.md), and [`plan.md`](./plan.md) for the full design spec and build plan.

## Features

- One-off jobs, executed at or after a target time
- Recurring jobs on a cron schedule *(implemented, not yet exercised end-to-end — see Verified section)*
- Exactly-once execution across concurrent worker instances
- HTTP callback execution with per-attempt outcome recording
- Exponential backoff retries
- Automatic dead-lettering after retries are exhausted
- Full execution history, correlated by `jobId` via MDC logging
- REST API: submit, get status, get run history, cancel
- JWT authentication (RS256) — every endpoint requires a valid bearer token; job ownership is enforced from the token's `sub` claim
- SSRF guard on callback URLs — blocks cloud metadata endpoints, loopback, and private-range addresses at both submission and execution time

## Stack

- Java 25
- Spring Boot 4.1.0
- PostgreSQL 16
- Flyway (schema migrations)
- Spring Security (OAuth2 Resource Server, JWT)
- Lombok
- Maven

## Prerequisites

- JDK 25
- Docker Desktop (for local Postgres via Docker Compose, and for Testcontainers-based integration tests)
- Maven (or use the included `./mvnw` wrapper)
- OpenSSL (to generate a local RSA key pair for JWT signing — see Authentication below)

## Local Setup

1. Start Postgres:
   ```bash
   docker compose up -d
   ```

2. Set the database password (or use the default in `application.yml`):
   ```bash
   export DB_PASSWORD=0000
   ```

3. Generate a local RSA key pair for JWT signing (dev only — see Authentication section):
   ```bash
   openssl genrsa -out jwt-private.pem 2048
   openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
   ```
   Point `application.yml` at the public key's absolute path under `spring.security.oauth2.resourceserver.jwt.public-key-location`.

4. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

   On startup, Flyway will automatically apply migrations from `src/main/resources/db/migration`.

## Authentication

All API endpoints require a valid JWT (`Authorization: Bearer <token>`), verified against a local RSA public key — no external identity provider needed for local development.

Generate a test token signed with the private key from setup:

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

The `sub` claim becomes the job's owner — only requests bearing a token with the matching `sub` can view, cancel, or list runs for a job. A different `sub` (or no token at all) gets `404`/`401` respectively; cross-owner access is deliberately indistinguishable from "job doesn't exist," to avoid leaking which job IDs are in use.

**Local dev only** — `jwt-private.pem` and `jwt-public.pem` are gitignored and must be generated locally; they are never committed.

## Submitting a Job

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

Then check status and run history:

```bash
curl http://localhost:8080/api/v1/jobs/<jobId> -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/v1/jobs/<jobId>/runs -H "Authorization: Bearer $TOKEN"
```

## Running Tests

```bash
./mvnw test
```

Integration tests (e.g. `JobClaimerImplConcurrencyTest`) use Testcontainers and require a running Docker daemon.

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
```

## Verified

Proven with real, manual end-to-end tests against a running instance — not just unit tests:

- Concurrent job claiming: 8 workers racing over 100 due jobs, zero duplicate claims (`JobClaimerImplConcurrencyTest`)
- One-off job success path: claim → HTTP 200 → job marked complete
- One-off job failure path: claim → HTTP 500 → exponential backoff → retry → `DEAD_LETTER`
- Accurate HTTP status codes recorded on both success and failure
- Live polling: newly inserted jobs are picked up without restarting the app
- Full REST API: submit, get, get-runs, cancel — via HTTP, not raw SQL
- Idempotency: resubmitting with the same key returns the original job, not a duplicate
- JWT authentication: valid signed tokens accepted; missing/invalid tokens rejected with `401`
- Ownership isolation: a job is only visible/cancellable by its creator's token `sub`; cross-owner access returns `404`, indistinguishable from a nonexistent job
- SSRF guard: callback URLs targeting the cloud metadata address (`169.254.169.254`) and loopback/internal addresses are rejected with `400` at submission time; legitimate external URLs are unaffected

Recurring (cron) jobs have the rescheduling logic implemented (`CronEvaluator`, `HttpCallbackExecutor.onSuccess`) but have not yet been run end-to-end — treat as unverified until tested.

## Roadmap

- End-to-end verification of recurring cron jobs
- Pause / resume / replay endpoints
- Prometheus metrics
- Kubernetes deployment, Terraform infrastructure

## Database

Schema is owned by Flyway (`src/main/resources/db/migration`), not Hibernate — `spring.jpa.hibernate.ddl-auto` is set to `none`.

Default local connection:
```
jdbc:postgresql://localhost:5433/job_scheduler
```