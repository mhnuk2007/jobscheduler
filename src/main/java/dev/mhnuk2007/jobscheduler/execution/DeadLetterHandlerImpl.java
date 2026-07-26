package dev.mhnuk2007.jobscheduler.execution;

import dev.mhnuk2007.jobscheduler.job.domain.Job;
import dev.mhnuk2007.jobscheduler.job.domain.JobStatus;
import dev.mhnuk2007.jobscheduler.job.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class DeadLetterHandlerImpl implements DeadLetterHandler {
    private static final Logger log = LoggerFactory.getLogger(DeadLetterHandlerImpl.class);
    private final JobRepository jobRepository;
    public DeadLetterHandlerImpl(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    @Transactional
    public void deadLetter(Job job, String reason) {
        job.setStatus(JobStatus.DEAD_LETTER);
        job.setClaimedBy(null);
        job.setClaimedAt(null);
        jobRepository.save(job);
        log.warn("job={} moved to DEAD_LETTER: {}", job.getJobId(), reason);
    }

    @Override
    @Transactional
    public void replay(Job job) {
        if(job.getStatus() != JobStatus.DEAD_LETTER) {
            throw new IllegalStateException("Job=" + job.getJobId() + " cannot be replayed from status="  + job.getStatus());
        }
        job.setStatus(JobStatus.SCHEDULED);
        job.setNextRunAt(Instant.now());
        jobRepository.save(job);
        log.info("job={} replayed from DEAD_LETTER", job.getJobId());


    }
}
