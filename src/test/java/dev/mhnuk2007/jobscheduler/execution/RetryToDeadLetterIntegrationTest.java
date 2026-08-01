package dev.mhnuk2007.jobscheduler.execution;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.mhnuk2007.jobscheduler.job.domain.*;
import dev.mhnuk2007.jobscheduler.job.repository.JobRepository;
import dev.mhnuk2007.jobscheduler.job.repository.JobRunRepository;
import dev.mhnuk2007.jobscheduler.security.CallbackUrlValidator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RetryToDeadLetterIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer ("postgres:16-alpine");
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("callback.denied-hosts", ()-> "169.254.169.254.169");
    }
    private WireMockServer wireMock;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobRunRepository jobRunRepository;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        jobRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("a job that always fails is retired maxTries times, then moved to DEAD_LETTER")
    void failingJob_exhaustedRetries_thenDeadLetter() {
        wireMock.stubFor(post(urlEqualTo("/callback"))
                .willReturn(aResponse().withStatus(500)));
        String callbackUrl = "http://localhost:" + wireMock.port() + "/callback";

        Job job = Job.builder()
                .jobId("job_retry_test_1")
                .ownerId("test-owner")
                .type(JobType.ONE_OFF)
                .status(JobStatus.SCHEDULED)
                .nextRunAt(Instant.now().minusSeconds(1))
                .maxRetries(3)
                .timeoutSeconds(10)
                .callback(Callback.builder()
                        .url(callbackUrl)
                        .method("POST")
                        .build())
                .build();
        jobRepository.save(job);

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    Job current = jobRepository.findByJobId("job_retry_test_1").orElseThrow();
                    assertThat(current.getStatus()).isEqualTo(JobStatus.DEAD_LETTER);
                });

        System.out.println("row count in job_runs = " + jobRunRepository.count());

        Job finalJob = jobRepository.findByJobId("job_retry_test_1").orElseThrow();
        List<JobRun> runs = jobRunRepository.findByJobIdOrderByAttemptAsc(finalJob.getId());

        assertThat(runs).hasSize(3);
        assertThat(runs).allSatisfy(run -> {
            assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
            assertThat(run.getHttpStatus()).isEqualTo(500);
        });

        wireMock.verify(3, postRequestedFor(urlEqualTo("/callback")));
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        @Primary
        CallbackUrlValidator callbackUrlValidator() {
            return url -> true; // allow-all for retry/dead-letter tests
        }
    }
}
