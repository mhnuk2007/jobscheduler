package dev.mhnuk2007.jobscheduler.scheduling;
import dev.mhnuk2007.jobscheduler.job.domain.Job;
import dev.mhnuk2007.jobscheduler.job.domain.JobStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class JobClaimerImpl implements JobClaimer {

    @PersistenceContext
    private EntityManager entityManager;

    private static final String CLAIM_QUERY = """
        UPDATE jobs
        SET status = :runningStatus,
            claimed_by = :workerId,
            claimed_at = :now
        WHERE id IN (
            SELECT id FROM jobs
            WHERE status = :scheduledStatus
              AND next_run_at <= :now
            ORDER BY next_run_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        )
        RETURNING *
        """;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED)
    public List<Job> claimDueJobs(String workerId, int batchSize) {
        Instant now = Instant.now();

        @SuppressWarnings("unchecked")
        List<Job> claimed = entityManager.createNativeQuery(CLAIM_QUERY, Job.class)
                .setParameter("runningStatus", JobStatus.RUNNING.name())
                .setParameter("scheduledStatus", JobStatus.SCHEDULED.name())
                .setParameter("workerId", workerId)
                .setParameter("now", now)
                .setParameter("batchSize", batchSize)
                .getResultList();

        return claimed;
    }
}