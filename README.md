# Job Scheduler

A distributed job scheduler built with Spring Boot — supports one-off and recurring (cron) jobs, executed exactly once across horizontally scaled worker replicas via PostgreSQL row-level locking (`SELECT ... FOR UPDATE SKIP LOCKED`).

See [`problemstatement.md`](./problemstatement.md) and [`functionalrequirements.md`](./functionalrequirements.md) for the full design spec.

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

4. Verify:
   ```bash
   curl http://localhost:8080/api/v1/jobs
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
│   ├── api/          # REST controllers, DTOs
│   ├── repository/   # Spring Data repositories, JSONB converters
│   └── service/       # Business logic
├── scheduling/       # JobClaimer (locking), JobPoller, CronEvaluator
├── execution/        # JobExecutor, RetryPolicy, DeadLetterHandler
├── security/         # JWT auth, callback URL validation (SSRF guard)
└── config/           # Security, Jackson, scheduler thread pool config
```

## Database

Schema is owned by Flyway (`src/main/resources/db/migration`), not Hibernate — `spring.jpa.hibernate.ddl-auto` is set to `none`.

Default local connection:
```
jdbc:postgresql://localhost:5432/job_scheduler
```