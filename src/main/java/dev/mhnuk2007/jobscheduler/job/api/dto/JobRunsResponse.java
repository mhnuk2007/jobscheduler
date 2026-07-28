package dev.mhnuk2007.jobscheduler.job.api.dto;

import java.time.Instant;
import java.util.List;

public record JobRunsResponse(
        String jobId,
        List<RunEntry> runs
) {
    public record RunEntry(
      String runId,
      int attempt,
      String status,
      Instant startedAt,
      Instant finishedAt,
      Integer httpStatus,
      String error
    ){}
}
