package dev.mhnuk2007.jobscheduler.execution;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.mhnuk2007.jobscheduler.job.domain.*;
import dev.mhnuk2007.jobscheduler.job.repository.JobRepository;
import dev.mhnuk2007.jobscheduler.job.repository.JobRunRepository;
import dev.mhnuk2007.jobscheduler.security.CallbackUrlValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.awaitility.Awaitility;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers
@SpringBootTest
public class ReplayIntegrationTest {
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

    @Autowired
    private DeadLetterHandler deadLetterHandler;

    @BeforeEach
    public void setup() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        jobRepository.deleteAll();
    }

    @AfterEach
    public void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("replaying a DEAD_LETTER job re-claims and re-executes it, producing a new successfull run")
    void replay_reExecutesJob_producesNewSuccessfulRun() {
        wireMock.stubFor(post(urlEqualTo("/callback"))
                .willReturn(aResponse().withStatus(500)));

        String callbackUrl = "http://localhost:" + wireMock.port() + "/callback";

        Job job = Job.builder()
                .jobId("job_replay_test_1")
                .ownerId("test_owner")
                .type(JobType.ONE_OFF)
                .status(JobStatus.DEAD_LETTER)
                .nextRunAt(Instant.now().minusSeconds(60))
                .maxRetries(1)
                .timeoutSeconds(10)
                .callback(
                        Callback.builder()
                                .url(callbackUrl)
                                .method("POST")
                                .build()
                ).build();
        jobRepository.save(job);

        int runCountBefore = jobRunRepository.findByJobIdOrderByAttemptAsc(job.getId()).size();
        assertThat(runCountBefore).isEqualTo(0);

        wireMock.stubFor(post(urlEqualTo("/callback"))
                .willReturn(aResponse().withStatus(200)));
        deadLetterHandler.replay(job);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    List<JobRun> runs =  jobRunRepository.findByJobIdOrderByAttemptAsc(job.getId());
                    assertThat(runs).hasSize(1);
                    assertThat(runs.get(0).getStatus()).isEqualTo(RunStatus.SUCCEEDED);
                    assertThat(runs.get(0).getHttpStatus()).isEqualTo(200);
                });
        Job finalJob = jobRepository.findByJobId("job_replay_test_1").orElseThrow();
        assertThat(finalJob.getStatus()).isEqualTo(JobStatus.CANCELLED);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/callback")));
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
