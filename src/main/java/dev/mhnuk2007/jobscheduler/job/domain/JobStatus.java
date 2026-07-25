package dev.mhnuk2007.jobscheduler.job.domain;

public enum JobStatus {
    SCHEDULED,
    RUNNING,
    ACTIVE,
    PAUSED,
    CANCELED,
    DEAD_LETTER
}
