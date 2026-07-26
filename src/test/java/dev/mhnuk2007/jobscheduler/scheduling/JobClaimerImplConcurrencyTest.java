package dev.mhnuk2007.jobscheduler.scheduling;

import dev.mhnuk2007.jobscheduler.job.domain.Job;
import dev.mhnuk2007.jobscheduler.job.domain.JobStatus;
import dev.mhnuk2007.jobscheduler.job.domain.JobType;
import dev.mhnuk2007.jobscheduler.job.domain.Callback;
import dev.mhnuk2007.jobscheduler.job.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@DisplayName("JobClaimerImpl under concurrent worker replicas")
class JobClaimerImplConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JobClaimer jobClaimer;

    @Autowired
    private JobRepository jobRepository;

    private static final int TOTAL_JOBS = 100;
    private static final int WORKER_COUNT = 8;
    private static final int BATCH_SIZE = 5;

    @BeforeEach
    void seedDueJobs() {
        jobRepository.deleteAll();
        List<Job> jobs = IntStream.range(0, TOTAL_JOBS)
                .mapToObj(i -> Job.builder()
                        .jobId("job_" + i)
                        .ownerId("test-owner")
                        .type(JobType.ONE_OFF)
                        .status(JobStatus.SCHEDULED)
                        .nextRunAt(Instant.now().minusSeconds(1))
                        .maxRetries(3)
                        .timeoutSeconds(30)
                        .callback(Callback.builder()
                                .url("https://example.com/hook")
                                .method("POST")
                                .build())
                        .build())
                .collect(Collectors.toList());
        jobRepository.saveAll(jobs);
    }

    @Test
    @DisplayName("no two workers ever claim the same job, and every job is eventually claimed exactly once")
    void concurrentClaims_areDisjointAndComplete() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(WORKER_COUNT);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<List<String>>> futures = new ArrayList<>();

        for (int w = 0; w < WORKER_COUNT; w++) {
            String workerId = "worker-" + w;
            futures.add(pool.submit(() -> {
                startGate.await();
                List<String> claimedByThisWorker = new ArrayList<>();
                int emptyPolls = 0;
                while (emptyPolls < 3) {
                    List<Job> batch = jobClaimer.claimDueJobs(workerId, BATCH_SIZE);
                    if (batch.isEmpty()) {
                        emptyPolls++;
                        Thread.sleep(20);
                        continue;
                    }
                    emptyPolls = 0;
                    batch.forEach(j -> claimedByThisWorker.add(j.getJobId()));
                }
                return claimedByThisWorker;
            }));
        }

        startGate.countDown();

        List<String> allClaimed = new ArrayList<>();
        for (Future<List<String>> f : futures) {
            allClaimed.addAll(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        Set<String> distinctClaimed = new HashSet<>(allClaimed);

        assertThat(distinctClaimed)
                .as("a job claimed more than once means the locking is broken")
                .hasSameSizeAs(allClaimed);

        assertThat(distinctClaimed).hasSize(TOTAL_JOBS);

        List<Job> allJobs = jobRepository.findAll();
        assertThat(allJobs).allSatisfy(job -> {
            assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
            assertThat(job.getClaimedBy()).isNotBlank();
        });
    }
}