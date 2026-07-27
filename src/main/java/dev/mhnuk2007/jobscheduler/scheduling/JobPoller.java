package dev.mhnuk2007.jobscheduler.scheduling;

import dev.mhnuk2007.jobscheduler.execution.JobExecutor;
import dev.mhnuk2007.jobscheduler.job.domain.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Component
public class JobPoller {
    private static final Logger log = LoggerFactory.getLogger(JobPoller.class);

    private final JobClaimer jobClaimer;
    private final JobExecutor jobExecutor;
    private final ExecutorService executorService;
    private final String workerId;
    private final int batchSize;

    public JobPoller(JobClaimer jobClaimer,
                     JobExecutor jobExecutor,
                     ExecutorService jobExecutorService,
                     @Value("${scheduler.worker-id}") String workerId,
                     @Value("${scheduler.poll-batch-size:20}") int batchSize) {
        this.jobClaimer = jobClaimer;
        this.jobExecutor = jobExecutor;
        this.executorService = jobExecutorService;
        this.workerId = workerId;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${scheduler.poll-interval-ms:1000}")
    public void poll() {
        List<Job> claimed;
        try {
            claimed = jobClaimer.claimDueJobs(workerId, batchSize);
        } catch (Exception e) {
            log.error("worker={} poll cycle failed to claim jobs", workerId, e);
            return;
        }
        if (claimed.isEmpty()) {
            return;
        }
        log.debug("worker={} claimed {} job(s)", workerId, claimed.size());
        claimed.forEach(this::dispatch);
    }

    private void dispatch(Job job) {
        executorService.submit(() -> {
            MDC.put("jobId", job.getJobId());
            try {
                jobExecutor.execute(job);
            }  catch (Exception e) {
                log.error("worker={} unhandled exception executing job={}", workerId, job.getJobId(), e);
            }  finally {
                MDC.remove("jobId");
            }
        });
    }
}
