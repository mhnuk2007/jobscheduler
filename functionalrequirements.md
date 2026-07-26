# Functional Requirements — Distributed Job Scheduler

## FR1 — Job Submission

- **FR1.1** — Client can submit a one-off job with a target execution time (`runAt`), an HTTP callback (URL, method, headers, body), and optional metadata.
- **FR1.2** — Client can submit a recurring job defined by a cron expression, with the same callback contract.
- **FR1.3** — Each submission returns a unique `jobId` immediately (async — submission does not block on execution).
- **FR1.4** — Client can attach idempotency semantics via an optional `idempotencyKey` — resubmitting with the same key returns the existing job instead of creating a duplicate.

## FR2 — Job Execution

- **FR2.1** — A job becomes eligible for execution when `runAt <= now` (one-off) or when the next cron fire time is reached (recurring).
- **FR2.2** — Exactly one worker instance executes a given job run at a time, even with N concurrent worker replicas.
- **FR2.3** — Execution = invoking the job's configured HTTP callback; a 2xx response marks the run `SUCCEEDED`, anything else (or timeout) marks it `FAILED`.
- **FR2.4** — A configurable execution timeout applies per job; a hung callback is treated as failure.

## FR3 — Retry & Failure Handling

- **FR3.1** — A failed run is retried up to `maxRetries` (job-level, default configurable) using exponential backoff.
- **FR3.2** — After exhausting retries, the job run moves to `DEAD_LETTER` status and stops retrying.
- **FR3.3** — Dead-lettered jobs can be manually replayed via API.

## FR4 — Job Lifecycle Management

- **FR4.1** — Client can fetch a job's current status and full run history.
- **FR4.2** — Client can cancel a pending (not-yet-executed) job.
- **FR4.3** — Client can pause/resume a recurring job without deleting it.

## FR5 — Auth & Access Control

- **FR5.1** — All endpoints require a valid JWT.
- **FR5.2** — Only the job's creator (subject claim) can view, cancel, or replay it.
- **FR5.3** — Callback URLs are validated against an allowlist/denylist to mitigate SSRF.

## FR6 — Observability

- **FR6.1** — Expose Prometheus-scrapeable metrics: queue depth, jobs succeeded/failed per minute, retry counts, worker pool utilization.
- **FR6.2** — Structured logs per job run, correlated by `jobId` and `runId`.

## FR7 — Scalability

- **FR7.1** — Adding worker replicas increases throughput linearly without config changes to producers.
- **FR7.2** — No single point of failure in the job-claiming mechanism (DB-based locking, not an in-memory leader).