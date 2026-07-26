package dev.mhnuk2007.jobscheduler.scheduling;

import java.time.Instant;

public interface CronEvaluator {
    Instant nextFireTime(String cronExpression, Instant after);
    boolean isValid(String cronExpression);
}
