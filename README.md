# Job Scheduler

![Java](https://img.shields.io/badge/Java-25-blue)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)

A distributed job scheduler built with Spring Boot. Jobs are claimed and executed exactly once across horizontally scaled worker replicas using PostgreSQL row-level locking (`SELECT ... FOR UPDATE SKIP LOCKED`) — no external lock service, no leader election.

See [`problemstatement.md`](./problemstatement.md) and [`functionalrequirements.md`](./functionalrequirements.md) for the full design spec.

## Features

- One-off jobs, executed at or after a target time
- Recurring jobs on a cron schedule *(implemented, not yet exercised end-to-end — see Verified section)*
- Exactly-once execution across concurrent worker instances
- HTTP callback execution with per-attempt outcome recording
- Exponential backoff retries
- Automatic dead-lettering after retries are exhausted
- Full execution history, correlated by `jobId` via MDC logging

## Stack

- Java 25
- Spring Boot 4.1.0
- PostgreSQL 16
- Flyway (schema migrations)
- Lombok
- Maven

## Prerequisites

- JDK 25
- Docker Desktop (for local Postgres via Docker Compose, and for Testcontainers-based integration tests)
- Maven (or use the included `./mvnw` wrapper)

## Local Setup

1. Start Postgres:
   ```bash
   docker compose up -d
   ```

2. Set the database password (or use the default in `application.yml`):
   ```bash
   export DB_PASSWORD=0000
   ```

3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

   On startup, Flyway will automatically apply migrations from `src/main/resources/db/migration`.

4. Since the REST API isn't built yet, seed a job directly to see it execute:
   ```sql
   INSERT INTO jobs (job_id, owner_id, type, status, next_run_at, max_retries, timeout_seconds, callback_url, callback_method)
   VALUES ('demo-1', 'demo', 'ONE_OFF', 'SCHEDULED', now() - interval '1 second', 3, 30, 'https://httpbin.org/post', 'POST');
   ```
   Within a second, `JobPoller` claims it and `HttpCallbackExecutor` runs the callback — check the result:
   ```sql
   SELECT status FROM jobs WHERE job_id = 'demo-1';
   SELECT attempt, status, http_status FROM job_runs
     WHERE job_id = (SELECT id FROM jobs WHERE job_id = 'demo-1');
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
│   └── repository/   # Spring Data repositories, JSONB converters
├── scheduling/       # JobClaimer (locking), JobPoller, CronEvaluator
├── execution/        # JobExecutor, HttpCallbackExecutor, RetryPolicy, DeadLetterHandler
└── config/           # SchedulerConfig (poller thread pool)
```

## Verified

Proven with real, manual end-to-end tests against a running instance — not just unit tests:

- Concurrent job claiming: 8 workers racing over 100 due jobs, zero duplicate claims (`JobClaimerImplConcurrencyTest`)
- One-off job success path: claim → HTTP 200 → job marked complete
- One-off job failure path: claim → HTTP 500 → exponential backoff → retry → `DEAD_LETTER`
- Accurate HTTP status codes recorded on both success and failure
- Live polling: newly inserted jobs are picked up without restarting the app

Recurring (cron) jobs have the rescheduling logic implemented (`CronEvaluator`, `HttpCallbackExecutor.onSuccess`) but have not yet been run end-to-end — treat as unverified until tested.

## Roadmap

- REST API (submit, cancel, pause/resume, replay)
- JWT authentication
- End-to-end verification of recurring cron jobs
- Callback URL validation (SSRF guard)
- Prometheus metrics
- Kubernetes deployment, Terraform infrastructure

## Database

Schema is owned by Flyway (`src/main/resources/db/migration`), not Hibernate — `spring.jpa.hibernate.ddl-auto` is set to `none`.

Default local connection:
```
jdbc:postgresql://localhost:5433/job_scheduler
```