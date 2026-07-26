package dev.mhnuk2007.jobscheduler.execution;

import dev.mhnuk2007.jobscheduler.job.domain.Job;

public interface JobExecutor {
    void execute(Job job);
}
