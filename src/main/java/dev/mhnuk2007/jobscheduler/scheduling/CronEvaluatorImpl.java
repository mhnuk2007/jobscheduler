package dev.mhnuk2007.jobscheduler.scheduling;

import org.springframework.scheduling.support.CronExpression;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;


@Component
public class CronEvaluatorImpl implements CronEvaluator {

    @Override
    public Instant nextFireTime(String cronExpression, Instant after) {
        CronExpression expr = parse(cronExpression);
        ZonedDateTime afterUtc = after.atZone(ZoneOffset.UTC);
        ZonedDateTime next = expr.next(afterUtc);
        if (next == null) {
            throw new IllegalStateException("cron expression '" +  cronExpression + "' has no future fire time");
        }
        return next.toInstant();
    }

    @Override
    public boolean isValid(String cronExpression) {
        try{
            CronExpression.parse(cronExpression);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    private CronExpression parse(String cronExpression) {
        try {
            return CronExpression.parse(cronExpression);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cron expression: " + cronExpression, e);
        }
    }
}
