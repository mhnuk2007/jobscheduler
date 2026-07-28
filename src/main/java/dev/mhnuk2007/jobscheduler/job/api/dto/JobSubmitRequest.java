package dev.mhnuk2007.jobscheduler.job.api.dto;

import dev.mhnuk2007.jobscheduler.job.domain.JobType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.Map;

public record JobSubmitRequest(
        @NotNull JobType type,
        Instant runAt,
        String cronExpression,
        @Valid @NotNull CallbackRequest callback,
        @Min(0) @Max(20) int maxRetries,
        @Min(1) @Max(3600) int timeoutSeconds,
        @Size(max = 128) String idempotencyKey
        ) {
    public record CallbackRequest(
            @NotBlank String url,
            @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE") String method,
            Map<String, String> headers,
            Object body
    ){}
}
