package dev.mhnuk2007.jobscheduler.job.api.mapper;

import dev.mhnuk2007.jobscheduler.job.api.dto.JobCreatedResponse;
import dev.mhnuk2007.jobscheduler.job.api.dto.JobResponse;
import dev.mhnuk2007.jobscheduler.job.api.dto.JobRunsResponse;
import dev.mhnuk2007.jobscheduler.job.domain.Job;
import dev.mhnuk2007.jobscheduler.job.domain.JobRun;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JobMapper {
    public JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getJobId(),
                job.getType().name(),
                job.getStatus(),
                job.getRunAt(),
                job.getCronExpression(),
                job.getNextRunAt(),
                job.getMaxRetries(),
                job.getCreatedAt()
        );
    }

    public JobCreatedResponse toCreatedResponse(Job job) {
        return new JobCreatedResponse(
                job.getJobId(),
                job.getStatus().name(),
                job.getCreatedAt()
        );
    }

    public JobRunsResponse toRunsResponse(String jobId, List<JobRun> runs) {

        List<JobRunsResponse.RunEntry> entries = runs.stream()
                .map(r -> new JobRunsResponse.RunEntry(
                        r.getRunId(),
                        r.getAttempt(),
                        r.getStatus().name(),
                        r.getStartedAt(),
                        r.getFinishedAt(),
                        r.getHttpStatus(),
                        r.getErrorMessage()
                )).toList();
        return new JobRunsResponse(jobId, entries);
    }
}
