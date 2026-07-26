package dev.mhnuk2007.jobscheduler.execution;

import dev.mhnuk2007.jobscheduler.job.domain.Job;

public interface DeadLetterHandler {
    void deadLetter(Job job, String reason);
    void replay(Job job);
}
