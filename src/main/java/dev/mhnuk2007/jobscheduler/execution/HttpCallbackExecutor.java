package dev.mhnuk2007.jobscheduler.execution;

import dev.mhnuk2007.jobscheduler.job.domain.*;
import dev.mhnuk2007.jobscheduler.job.repository.JobRepository;
import dev.mhnuk2007.jobscheduler.job.repository.JobRunRepository;
import dev.mhnuk2007.jobscheduler.scheduling.CronEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class HttpCallbackExecutor implements JobExecutor {
    private static final Logger log = LoggerFactory.getLogger(HttpCallbackExecutor.class);
    private final RestClient restClient;
    private final JobRepository jobRepository;
    private final JobRunRepository jobRunRepository;
    private final RetryPolicy retryPolicy;
    private final DeadLetterHandler deadLetterHandler;
    private final CronEvaluator cronEvaluator;
    public HttpCallbackExecutor(RestClient.Builder restClientBuilder,
                                JobRepository jobRepository,
                                JobRunRepository jobRunRepository,
                                RetryPolicy retryPolicy,
                                DeadLetterHandler deadLetterHandler,
                                CronEvaluator cronEvaluator) {
        this.restClient = restClientBuilder.build();
        this.jobRepository = jobRepository;
        this.jobRunRepository = jobRunRepository;
        this.retryPolicy = retryPolicy;
        this.deadLetterHandler = deadLetterHandler;
        this.cronEvaluator = cronEvaluator;
    }
    @Override
    public void execute(Job job) {
        int attempt = nextAttemptNumber(job);
        Instant startedAt = Instant.now();
        CallbackOutcome outcome = invokeCallback(job);
        Instant finishedAt = Instant.now();

        RunStatus runStatus = outcome.succeeded() ? RunStatus.SUCCEEDED : RunStatus.FAILED;
        recordRun(job, attempt, runStatus, outcome.httpStatus(), outcome.errorMessage(), startedAt, finishedAt);
        if(outcome.succeeded()) onSuccess(job);
        else onFailure(job, attempt);
    }

    private void recordRun(Job job, int attempt, RunStatus status, Integer httpStatus, String errorMessage, Instant startedAt, Instant finishedAt) {
        JobRun run = JobRun.builder()
                .runId(UUID.randomUUID().toString())
                .jobId(job.getId())
                .attempt(attempt)
                .status(status)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .httpStatus(httpStatus)
                .errorMessage(errorMessage)
                .build();
        jobRunRepository.save(run);
    }

    private CallbackOutcome invokeCallback(Job job) {
        Callback cb = job.getCallback();

        try{
            var response = restClient.method(HttpMethod.valueOf(cb.getMethod()))
                    .uri(cb.getUrl())
                    .headers(h ->{if(cb.getHeaders() != null) cb.getHeaders().forEach(h::add);})
                    .body(cb.getBody() == null ? "" : cb.getBody())
                    .retrieve()
                    .toBodilessEntity();

            HttpStatusCode status = response.getStatusCode();
            boolean success = status.is2xxSuccessful();
            return new CallbackOutcome(success, status.value(), success ? null : "non-2xx response: " + status.value());
        } catch(Exception e) {
            return new CallbackOutcome(false, null, "callback invocation error: " + e.getMessage());
        }

    }

    @Transactional
    protected void onSuccess(Job job) {
        if(job.getType() == JobType.RECURRING){
            Instant next = cronEvaluator.nextFireTime(job.getCronExpression(), Instant.now());
            job.setNextRunAt(next);
            job.setStatus(JobStatus.SCHEDULED);
        } else {
            job.setStatus(JobStatus.CANCELLED);
        }
        job.setClaimedBy(null);
        job.setClaimedAt(null);
        jobRepository.save(job);
    }

    @Transactional
    protected void onFailure(Job job, int attempt) {
        if(attempt >= job.getMaxRetries()){
            deadLetterHandler.deadLetter(job, "max retries exhausted");
            return;
        }
        Duration backoff = retryPolicy.nextDelay(attempt);
        job.setNextRunAt(Instant.now().plus(backoff));
        job.setStatus(JobStatus.SCHEDULED);
        job.setClaimedBy(null);
        job.setClaimedAt(null);
        jobRepository.save(job);

    }

    private int nextAttemptNumber(Job job) {
        return jobRunRepository.countByJobId(job.getId()) + 1;

    }
    

    private record CallbackOutcome(boolean succeeded, Integer httpStatus, String errorMessage) {}
}
