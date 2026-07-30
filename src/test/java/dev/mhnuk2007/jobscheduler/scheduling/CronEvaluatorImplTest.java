package dev.mhnuk2007.jobscheduler.scheduling;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CronEvaluatorImplTest {

    @Test
    void isValid_acceptsStandardExpression() {
        CronEvaluatorImpl evaluator = new CronEvaluatorImpl();
        assertThat(evaluator.isValid("0 */1 * * * *")).isTrue();
    }
}