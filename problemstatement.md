# Problem Statement — Distributed Job Scheduler

## Background

Modern backend systems frequently need to execute units of work outside the request-response cycle — sending emails, generating reports, syncing data with third-party services, or running scheduled maintenance tasks. As systems scale horizontally across multiple instances, naive implementations (e.g., an in-process `@Scheduled` method or a single cron job) break down: they cause duplicate execution when replicated across pods, offer no retry or failure isolation, and provide no visibility into job health.

## Problem

Design and build a **Distributed Job Scheduler** — a backend service that allows client applications to submit units of work (one-off tasks or recurring cron-based jobs) for reliable, exactly-once execution across a horizontally scaled pool of worker instances, with built-in retry handling, failure isolation, and observability.

The system must solve the following core challenges:

1. **Reliable delivery** — A submitted job must be executed even if the worker processing it crashes mid-execution.
2. **No duplicate execution** — When multiple worker replicas run concurrently, a single job must never be picked up and executed by more than one worker at the same time.
3. **Failure handling** — Jobs that fail must be retried with backoff, and jobs that exhaust their retry budget must be moved to a dead-letter queue rather than lost or retried indefinitely.
4. **Scheduling flexibility** — The system must support both one-off jobs (execute once, at or after a given time) and recurring jobs (defined by a cron expression).
5. **Secure access** — Job submission and management APIs must be authenticated and authorized, since arbitrary job submission is a potential attack surface (e.g., callback URL abuse, SSRF).
6. **Observability** — Operators must be able to see queue depth, job success/failure rates, and per-job execution history to diagnose problems in production.
7. **Horizontal scalability** — Throughput must increase as worker replicas are added, without requiring changes to job producers.

## Goal

Deliver a Spring Boot–based system, containerized and deployable to Kubernetes, that demonstrates correct distributed job coordination under concurrent worker replicas, with infrastructure provisioned as code and metrics exposed for monitoring — serving as a practical demonstration of distributed systems and system design principles applied to a real backend service.

## Out of Scope (v1)

- Multi-tenant job isolation / per-tenant rate limiting
- A full UI dashboard (a minimal status page is optional, not required)
- Cross-region/multi-datacenter job replication