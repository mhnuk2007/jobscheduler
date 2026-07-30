Week 1 — Foundation & Correctness

Day 1 — Project bootstrap + schema

Finalize pom.xml (the corrected dependency set from above)
Confirm the project actually builds and boots (mvn spring-boot:run with no code beyond JobSchedulerApplication)
Write V1__init_schema.sql, wire Flyway, confirm migration runs against local Postgres (Docker Compose, not yet K8s)
Set up application.yml / application-test.yml

Day 2 — Domain layer, audited for Boot 4 / Jackson 3

Job, JobRun, JobType, JobStatus, RunStatus, Callback — re-verify these compile against Jakarta/Hibernate versions Boot 4.1 actually pulls in
Rewrite JacksonConfig against Jackson 3 (tools.jackson, JsonMapper not ObjectMapper as the auto-configured bean) — this was flagged as broken and needs doing before anything JSON-serializes
Fix the JSONB converters (JsonConverters.java) to use the Jackson 3 API consistently
Get one full round-trip working: insert a Job via repository, read it back, confirm Callback embeddable (headers/body JSONB) serializes correctly

Day 3 — Repositories + concurrency test, proven early

JobRepository, JobRunRepository
JobClaimer/JobClaimerImpl — the SELECT FOR UPDATE SKIP LOCKED query
Write and pass JobClaimerImplConcurrencyTest against Testcontainers Postgres — this is the riskiest, most novel piece of the whole project; proving it early means every day after this builds on a mechanism you know actually works, not one you're hoping works

Day 4 — Execution pipeline

JobExecutor/HttpCallbackExecutor — confirm WebClient (or RestClient, given the webflux/webclient starter decision from earlier) actually invokes a stub HTTP endpoint and records outcomes correctly
RetryPolicy/ExponentialBackoffPolicy + its unit test
DeadLetterHandler/DeadLetterHandlerImpl
CronEvaluator/CronEvaluatorImpl + unit tests for next-fire-time edge cases

Day 5 — Scheduling loop, end to end

JobPoller + SchedulerConfig (executor pool bean)
Manual end-to-end test: submit a job directly via repository, start the app, watch it get claimed, executed, and either succeed or retry — first time you see the whole loop turn over live
StructuredLogger, JobMetrics/JobMetricsImpl, MetricsConfig wired in so you can see this happening in logs/metrics, not just infer it
Week 2 — API, Security, Hardening

Day 6 — API layer

DTOs, JobMapper (confirm MapStruct annotation processing actually runs under Boot 4's compiler plugin config)
JobService/JobServiceImpl, IdempotencyService/IdempotencyServiceImpl
JobController — get POST /jobs and GET /jobs/{id} working without auth first, to isolate API bugs from security bugs

Day 7 — Security

SecurityConfig with JWT resource-server config — this is where Boot 4's OAuth2 resource-server starter needs verifying against its 4.1 config surface, since some property names changed in the migration
CallbackUrlValidator/CallbackUrlValidatorImpl (SSRF guard) + tests: assert it blocks 169.254.169.254, loopback, and a rebinding attempt
Wire JWT auth into the full request path; test with a real (or locally-issued test) JWT

Day 8 — Remaining lifecycle endpoints + error handling

cancel, pause, resume, replay endpoints and their JobService methods
GlobalExceptionHandler — confirm every exception type maps to the right status
Resolve the idempotency-key scoping decision flagged earlier (global vs. per-owner unique constraint) — pick one and update the migration if needed

Day 9 — Test hardening

Fill test coverage gaps: JobServiceImpl unit tests, JobController slice tests (@AutoConfigureMockMvc, which is now opt-in under Boot 4 rather than automatic with @SpringBootTest)
Integration test for the full retry-to-dead-letter path (fail a job maxRetries times, assert DEAD_LETTER)
Integration test for the replay path

Day 10 — Buffer / catch-up

Nothing new — this day exists because Day 2's Jackson 3 rewrite or Day 7's security config will very likely eat into other days' time. Use whatever's left to close gaps.
Week 3 — Containerize & Deploy

Day 11 — Docker

Dockerfile, confirm the jar builds and runs in a container talking to a containerized Postgres via docker-compose
Fix the <includeOptional> Maven plugin note from the Boot 4 migration guide if Lombok is excluded from the fat jar

Day 12 — Terraform: networking + data

vpc.tf, rds.tf — terraform plan, review the resource count/cost, apply into a scratch AWS account or a tightly scoped dev environment

Day 13 — Terraform: EKS + IAM

eks.tf, iam.tf — apply, confirm aws eks update-kubeconfig works and kubectl get nodes shows 3 nodes

Day 14 — Kubernetes deploy

Push the image to a registry (ECR, since you're already in AWS)
Apply deployment.yaml, configmap.yaml — wire the ExternalSecrets piece (or a manual kubectl create secret as an interim step if ESO setup is more than a day's work)
Confirm 3 pods come up healthy, readiness probes pass

Day 15 — Prove the distributed claim under real replicas

With 3 real pods running, submit a batch of jobs and confirm via logs/metrics (worker_id tag) that claims are actually spread across pods with zero duplicates — this is the moment the whole project's core claim becomes demonstrably true in a live cluster, not just in a Testcontainers test
Wire ServiceMonitor if you have Prometheus Operator available, or at minimum confirm /actuator/prometheus is scrapeable
After that

README, HPA load test (optional), and polish — but the plan above gets you to a genuinely working, deployed, provably-correct system by end of Week 3, which is the real milestone for the portfolio piece.