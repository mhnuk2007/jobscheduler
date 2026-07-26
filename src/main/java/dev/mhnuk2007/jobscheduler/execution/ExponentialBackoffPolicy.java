package dev.mhnuk2007.jobscheduler.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class ExponentialBackoffPolicy implements RetryPolicy {
    private final long baseMillis;
    private final long capMillis;

    public ExponentialBackoffPolicy(
            @Value("${retry.base-delay-ms:1000}") long baseMillis,
            @Value("${retry.max-delay-ms:300000}") long capMillis) {
        this.baseMillis = baseMillis;
        this.capMillis = capMillis;
    }

    @Override
    public Duration nextDelay(int completedAttempt) {
        long exponential = baseMillis * (1L << Math.min(completedAttempt, 20));
        long capped = Math.min(capMillis, exponential);
        long jittered = ThreadLocalRandom.current().nextLong(0, capped + 1);

        return  Duration.ofMillis(jittered);
    }
}
