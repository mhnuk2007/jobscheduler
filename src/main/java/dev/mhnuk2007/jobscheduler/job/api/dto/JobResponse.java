package dev.mhnuk2007.jobscheduler.job.api.dto;

import dev.mhnuk2007.jobscheduler.job.domain.JobStatus;

import java.time.Instant;

public record JobResponse(
        String jobId,
        String type,
        JobStatus status,
        Instant runAt,
        String cronExpression,
        Instant nextRunAt,
        int maxRetries,
        Instant createdAt
) {
}
