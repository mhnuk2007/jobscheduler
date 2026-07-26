package dev.mhnuk2007.jobscheduler.execution;

import java.time.Duration;

public interface RetryPolicy {
    Duration nextDelay(int completedAttempt);
}
