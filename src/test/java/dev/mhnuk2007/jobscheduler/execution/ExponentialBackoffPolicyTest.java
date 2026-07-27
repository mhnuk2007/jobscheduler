package dev.mhnuk2007.jobscheduler.execution;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ExponentialBackoffPolicyTest {

    @Test
    void nextDelay_neverExceedsCap() {
        RetryPolicy policy = new ExponentialBackoffPolicy(1000, 300_000);
        Duration delay = policy.nextDelay(50);
        assertThat(delay).isLessThanOrEqualTo(Duration.ofMillis(300_000));
    }
}