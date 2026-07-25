package dev.mhnuk2007.jobscheduler.scheduling;

import dev.mhnuk2007.jobscheduler.job.domain.Job;

import java.util.List;

public interface JobClaimer {
    List<Job> claimDueJobs(String workerId, int batchSize);
}
