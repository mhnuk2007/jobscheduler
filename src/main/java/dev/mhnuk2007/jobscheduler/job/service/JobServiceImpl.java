package dev.mhnuk2007.jobscheduler.job.service;

import dev.mhnuk2007.jobscheduler.execution.DeadLetterHandler;
import dev.mhnuk2007.jobscheduler.job.api.dto.JobSubmitRequest;
import dev.mhnuk2007.jobscheduler.job.domain.*;
import dev.mhnuk2007.jobscheduler.job.exception.IllegalJobStateException;
import dev.mhnuk2007.jobscheduler.job.exception.InvalidJobRequestException;
import dev.mhnuk2007.jobscheduler.job.exception.JobNotFoundException;
import dev.mhnuk2007.jobscheduler.job.repository.JobRepository;
import dev.mhnuk2007.jobscheduler.job.repository.JobRunRepository;
import dev.mhnuk2007.jobscheduler.scheduling.CronEvaluator;
import dev.mhnuk2007.jobscheduler.security.CallbackUrlValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final JobRunRepository jobRunRepository;
    private final CronEvaluator cronEvaluator;
    private final CallbackUrlValidator callbackUrlValidator;
    private final DeadLetterHandler deadLetterHandler;

    public JobServiceImpl(JobRepository jobRepository, JobRunRepository jobRunRepository, CronEvaluator cronEvaluator, CallbackUrlValidator callbackUrlValidator, DeadLetterHandler deadLetterHandler) {
        this.jobRepository = jobRepository;
        this.jobRunRepository = jobRunRepository;
        this.cronEvaluator = cronEvaluator;
        this.callbackUrlValidator = callbackUrlValidator;
        this.deadLetterHandler = deadLetterHandler;
    }

    @Override
    @Transactional
    public Job submit(JobSubmitRequest request, String ownerId) {
        if (!callbackUrlValidator.isAllowed(request.callback().url())) {
            throw new InvalidJobRequestException("callback URL is not permitted" + request.callback().url());
        }
        if (request.idempotencyKey() != null) {
            Job existing = jobRepository.findByOwnerIdAndIdempotencyKey(ownerId, request.idempotencyKey()).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        validateTypeSpecificFields(request);
        Instant nextRunAt = switch (request.type()) {
            case ONE_OFF -> request.runAt();
            case RECURRING -> cronEvaluator.nextFireTime(request.cronExpression(), Instant.now());
        };

        Job job = Job.builder()
                .jobId("job_" + UUID.randomUUID())
                .ownerId(ownerId)
                .type(request.type())
                .status(JobStatus.SCHEDULED)
                .runAt(request.runAt())
                .cronExpression(request.cronExpression())
                .nextRunAt(nextRunAt)
                .maxRetries(request.maxRetries() == null || request.maxRetries() == 0 ? 3 : request.maxRetries())
                .timeoutSeconds(request.timeoutSeconds() == null || request.timeoutSeconds() == 0 ? 30 : request.timeoutSeconds())
                .idempotencyKey(request.idempotencyKey())
                .callback(toCallback(request.callback()))
                .build();
        return jobRepository.save(job);
    }

    @Override
    public Job getOwned(String jobId, String ownerId) {
        return findOwnedOrThrow(jobId, ownerId);
    }

    @Override
    public List<JobRun> getRunsOwned(String jobId, String ownerId) {
        Job job = findOwnedOrThrow(jobId, ownerId);
        return jobRunRepository.findByJobIdOrderByAttemptAsc(job.getId());
    }

    @Override
    @Transactional
    public void cancelOwned(String jobId, String ownerId) {
        Job job = findOwnedOrThrow(jobId, ownerId);
        if (job.getStatus() == JobStatus.RUNNING) {
            throw new IllegalJobStateException("cannot cancel job currently RUNNING: " + jobId);
        }
        job.setStatus(JobStatus.CANCELLED);
        jobRepository.save(job);
    }

    @Override
    @Transactional
    public Job pauseOwned(String jobId, String ownerId) {
        Job job = findOwnedOrThrow(jobId, ownerId);
        requireType(job, JobType.RECURRING, "pause");
        job.setStatus(JobStatus.PAUSED);
        return jobRepository.save(job);
    }

    @Override
    @Transactional
    public Job resumeOwned(String jobId, String ownerId) {
        Job job = findOwnedOrThrow(jobId, ownerId);
        requireType(job, JobType.RECURRING, "resume");
        if (job.getStatus() != JobStatus.PAUSED) {
            throw new IllegalJobStateException("cannot resume job not currently PAUSED: " + jobId);
        }
        job.setStatus(JobStatus.SCHEDULED);
        job.setNextRunAt(cronEvaluator.nextFireTime(job.getCronExpression(), Instant.now()));
        return jobRepository.save(job);
    }

    @Override
    @Transactional
    public void replayOwned(String jobId, String ownerId) {
        Job job = findOwnedOrThrow(jobId, ownerId);
        deadLetterHandler.replay(job);
    }

    private Job findOwnedOrThrow(String jobId, String ownerId) {
        Job job = jobRepository.findByJobId(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        if (!job.getOwnerId().equals(ownerId)) {
            throw new JobNotFoundException(jobId);
        }
        return job;
    }

    private void validateTypeSpecificFields(JobSubmitRequest request) {
        switch (request.type()) {
            case ONE_OFF -> {
                if (request.runAt() == null) {
                    throw new InvalidJobRequestException("runAt is required for ONE_OFF jobs");
                }
            }
            case RECURRING -> {
                if (request.cronExpression() == null || !cronEvaluator.isValid(request.cronExpression())) {
                    throw new InvalidJobRequestException("a valid cronExpression is required for RECURRING jobs");
                }
            }
        }
    }

    private Callback toCallback(JobSubmitRequest.CallbackRequest cb) {
        return Callback.builder()
                .url(cb.url())
                .method(cb.method() == null ? "POST" : cb.method())
                .headers(cb.headers())
                .body(cb.body())
                .build();
    }

    private void requireType(Job job, JobType expected, String action) {
        if (job.getType() != expected) {
            throw new IllegalJobStateException(
                    "cannot " + action + " a " + job.getType() + " job: " + job.getJobId());
        }
    }
}