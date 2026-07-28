package dev.mhnuk2007.jobscheduler.job.api.dto;

import java.time.Instant;

public record JobCreatedResponse(
        String jobId,
        String status,
        Instant createdAt
) {
}
