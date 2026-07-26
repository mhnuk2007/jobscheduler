package dev.mhnuk2007.jobscheduler.job.domain;

/**
 * SCHEDULED --(claimed)--> RUNNING
 * RUNNING --(success, ONE_OFF)--> CANCELLED (terminal)
 * RUNNING --(success, RECURRING)--> SCHEDULED (next cron fire)
 * RUNNING --(fail, retries remain)--> SCHEDULED (next_run_at pushed by backoff)
 * RUNNING --(fail, exhausted)--> DEAD_LETTER
 * SCHEDULED/ACTIVE --(cancel)--> CANCELLED
 * ACTIVE --(pause, RECURRING only)--> PAUSED
 * PAUSED --(resume)--> SCHEDULED
 * DEAD_LETTER --(replay)--> SCHEDULED
 *
 * Only SCHEDULED rows with a due next_run_at are ever matched by
 * JobClaimer's claim query.
 */
public enum JobStatus {
    SCHEDULED,
    RUNNING,
    ACTIVE,
    PAUSED,
    CANCELLED,
    DEAD_LETTER
}