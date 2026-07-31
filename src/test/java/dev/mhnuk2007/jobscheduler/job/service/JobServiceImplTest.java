package dev.mhnuk2007.jobscheduler.job.service;

import dev.mhnuk2007.jobscheduler.execution.DeadLetterHandler;
import dev.mhnuk2007.jobscheduler.job.api.dto.JobSubmitRequest;
import dev.mhnuk2007.jobscheduler.job.domain.Job;
import dev.mhnuk2007.jobscheduler.job.domain.JobStatus;
import dev.mhnuk2007.jobscheduler.job.domain.JobType;
import dev.mhnuk2007.jobscheduler.job.exception.IllegalJobStateException;
import dev.mhnuk2007.jobscheduler.job.exception.InvalidJobRequestException;
import dev.mhnuk2007.jobscheduler.job.exception.JobNotFoundException;
import dev.mhnuk2007.jobscheduler.job.repository.JobRepository;
import dev.mhnuk2007.jobscheduler.job.repository.JobRunRepository;
import dev.mhnuk2007.jobscheduler.scheduling.CronEvaluator;
import dev.mhnuk2007.jobscheduler.security.CallbackUrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JobServiceImplTest {
    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobRunRepository jobRunRepository;
    @Mock
    private CronEvaluator cronEvaluator;
    @Mock
    private CallbackUrlValidator callbackUrlValidator;
    @Mock
    private DeadLetterHandler deadLetterHandler;

    private JobServiceImpl jobService;
    private static final String OWNER = "demo-user";

    @BeforeEach
    void setUp() {
        jobService = new JobServiceImpl(jobRepository, jobRunRepository, cronEvaluator, callbackUrlValidator, deadLetterHandler);
    }

    private JobSubmitRequest.CallbackRequest validCallback() {
        return new JobSubmitRequest.CallbackRequest("https://httpbin.org/post", "POST", null, null);
    }

    private void allowAllCallbackUrls() {
        when(callbackUrlValidator.isAllowed(anyString())).thenReturn(true);
    }

    @Nested
    @DisplayName("submit — ONE_OFF")
    class SubmitOneOff {
        @Test
        @DisplayName("succeed with a valid runAt")
        void succeedWithValidRunAt() {
            allowAllCallbackUrls();
            JobSubmitRequest request = new JobSubmitRequest(JobType.ONE_OFF, Instant.now().plusSeconds(60), null, validCallback(), 3, 30, null);
            when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
            Job result = jobService.submit(request, OWNER);

            assertThat(result.getType()).isEqualTo(JobType.ONE_OFF);
            assertThat(result.getStatus()).isEqualTo(JobStatus.SCHEDULED);
            assertThat(result.getOwnerId()).isEqualTo(OWNER);
            verify(jobRepository).save(any(Job.class));
        }

        @Test
        @DisplayName("rejects a missing runAt")
        void rejectsMissingRunAt() {
            allowAllCallbackUrls();
            JobSubmitRequest request = new JobSubmitRequest(JobType.ONE_OFF, null, null, validCallback(), 3, 30, null);

            assertThatThrownBy(() -> jobService.submit(request, OWNER))
                    .isInstanceOf(InvalidJobRequestException.class)
                    .hasMessageContaining("runAt");
            verify(jobRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("submit — RECURRING")
    class SubmitRecurring {

        @Test
        @DisplayName("succeeds with a valid 6-field cron expression")
        void succeedsWithValidCron() {
            allowAllCallbackUrls();
            JobSubmitRequest request = new JobSubmitRequest(JobType.RECURRING, null, "0 */1 * * * *", validCallback(), 3, 30, null);
            when(cronEvaluator.isValid("0 */1 * * * *")).thenReturn(true);
            when(cronEvaluator.nextFireTime(eq("0 */1 * * * *"), any())).thenReturn(Instant.now().plusSeconds(60));
            when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

            Job result = jobService.submit(request, OWNER);

            assertThat(result.getType()).isEqualTo(JobType.RECURRING);
            assertThat(result.getStatus()).isEqualTo(JobStatus.SCHEDULED);
        }

        @Test
        @DisplayName("rejects a missing cronExpression — regression for the original 400 bug")
        void rejectMissingCron() {
            allowAllCallbackUrls();
            JobSubmitRequest request = new JobSubmitRequest(JobType.RECURRING, null, null, validCallback(), 3, 30, null);
            assertThatThrownBy(() -> jobService.submit(request, OWNER))
                    .isInstanceOf(InvalidJobRequestException.class)
                    .hasMessageContaining("cronExpression");

            verifyNoInteractions(cronEvaluator);
            verify(jobRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects a malformed cronExpression")
        void rejectInvalidCron() {
            allowAllCallbackUrls();
            JobSubmitRequest request = new JobSubmitRequest(JobType.RECURRING, null, "not-a-cron", validCallback(), 3, 30, null);
            when(cronEvaluator.isValid("not-a-cron")).thenReturn(false);
            assertThatThrownBy(() -> jobService.submit(request, OWNER))
                    .isInstanceOf(InvalidJobRequestException.class);
            verify(jobRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects a 5-field Unix-style cron — regression for the field-count bug")
        void rejectsFiveFieldCron() {
            allowAllCallbackUrls();
            JobSubmitRequest request = new JobSubmitRequest(JobType.RECURRING, null, "*/1 * * * *", validCallback(), 3, 30, null);
            when(cronEvaluator.isValid("*/1 * * * *")).thenReturn(false);
            assertThatThrownBy(() -> jobService.submit(request, OWNER))
                    .isInstanceOf(InvalidJobRequestException.class);
            verify(jobRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("submit — idempotency")
    class SubmitIdempotency {

        @Test
        @DisplayName("returns the existing job for the same owner and key, without creating a new one")
        void sameOwnerSameKeyReturnsExisting() {
            allowAllCallbackUrls();
            Job existing = Job.builder().jobId("job_existing").ownerId(OWNER).build();
            JobSubmitRequest request = new JobSubmitRequest(JobType.ONE_OFF, Instant.now().plusSeconds(60), null, validCallback(), 3, 30, "shared-key");

            when(jobRepository.findByOwnerIdAndIdempotencyKey(OWNER, "shared-key"))
                    .thenReturn(Optional.of(existing));
            Job result = jobService.submit(request, OWNER);
            assertThat(result).isSameAs(existing);
            verify(jobRepository, never()).save(any());

        }

        @Test
        @DisplayName("a different owner with the same key creates an independent job — regression for the cross-owner bug")
        void differentOwnerSameKeyCreatesNew() {
            allowAllCallbackUrls();
            JobSubmitRequest request = new JobSubmitRequest(JobType.ONE_OFF, Instant.now().plusSeconds(60), null, validCallback(), 3, 30, "shared-key");

            when(jobRepository.findByOwnerIdAndIdempotencyKey("owner-b", "shared-key"))
                    .thenReturn(Optional.empty());
            when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

            Job result = jobService.submit(request, "owner-b");

            assertThat(result.getOwnerId()).isEqualTo("owner-b");
            verify(jobRepository).save(any(Job.class));
        }
    }

    @Nested
    @DisplayName("submit — SSRF guard")
    class SubmitSsrfGuard {
        @Test
        @DisplayName("rejects a callback URL the validator disallow")
        void rejectDisallowedCallbackUrl() {
            allowAllCallbackUrls();
            JobSubmitRequest.CallbackRequest badCallback = new JobSubmitRequest.CallbackRequest("http://169.254.169.254/latest/meta-data", "GET", null, null);
            JobSubmitRequest request = new JobSubmitRequest(JobType.ONE_OFF, Instant.now().plusSeconds(60), null, badCallback, 3, 30, "shared-key");

            when(callbackUrlValidator.isAllowed("http://169.254.169.254/latest/meta-data")).thenReturn(false);
            assertThatThrownBy(() -> jobService.submit(request, OWNER))
                    .isInstanceOf(InvalidJobRequestException.class);
            verify(jobRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("ownership isolation")
    class OwnershipIsolation {

        @Test
        @DisplayName("getOwned throws JobNotFoundException for a nonexistent jobId")
        void getOwned_notFound() {
            when(jobRepository.findByJobId("job_ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> jobService.getOwned("job_ghost", OWNER))
                    .isInstanceOf(JobNotFoundException.class);
        }

        @Test
        @DisplayName("getOwned throws JobNotFoundException (not a distinct forbidden error) when owned by someone else")
        void getOwned_wrongOwner_returnsNoFoundNotForbidden() {
            Job job = Job.builder().jobId("job_1").ownerId("someone_else").build();
            when(jobRepository.findByJobId("job_1")).thenReturn(Optional.of(job));
            assertThatThrownBy(() -> jobService.getOwned("job_1", OWNER))
                    .isInstanceOf(JobNotFoundException.class);
        }
    }

    @Test
    @DisplayName("getOwned succeeds when the when the owner matches")
    void getOwned_correctOwner_succeeds() {
        Job job = Job.builder().jobId("job_1").ownerId(OWNER).build();
        when(jobRepository.findByJobId("job_1")).thenReturn(Optional.of(job));

        Job result = jobService.getOwned("job_1", OWNER);
        assertThat(result).isSameAs(job);
    }

    @Nested
    @DisplayName("cancelOwned")
    class CancelOwned {
        @Test
        @DisplayName("cancel a SCHEDULED job")
        void cancelScheduledJob() {
            Job job = Job.builder().jobId("job_1").ownerId(OWNER).status(JobStatus.SCHEDULED).build();
            when(jobRepository.findByJobId("job_1")).thenReturn(Optional.of(job));
            jobService.cancelOwned("job_1", OWNER);
            assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
            verify(jobRepository).save(job);
        }

        @Test
        @DisplayName("reject cancelling a running job")
        void rejectCancellingRunningJob() {
            Job job = Job.builder().jobId("job_1").ownerId(OWNER).status(JobStatus.RUNNING).build();
            when(jobRepository.findByJobId("job_1")).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> jobService.cancelOwned("job_1", OWNER))
                    .isInstanceOf(IllegalJobStateException.class);

            verify(jobRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("pauseOwned / resumeOwned — type guards")
    class PauseResumeTypeGuards {
        @Test
        @DisplayName("pause succeed on a recurring job")
        void pauseSucceedOnRecurring() {
            Job job = Job.builder().jobId("job_1").ownerId(OWNER).type(JobType.RECURRING).status(JobStatus.SCHEDULED).build();
            when(jobRepository.findByJobId("job_1")).thenReturn(Optional.of(job));
            when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

            Job result = jobService.pauseOwned("job_1", OWNER);
            assertThat(result.getStatus()).isEqualTo(JobStatus.PAUSED);
        }

        @Test
        @DisplayName("pause rejects a ONE_OFF job — regression for the verified 409 behavior")
        void pauseRejectsOneOff() {
            Job job = Job.builder().jobId("job_1").ownerId(OWNER).type(JobType.ONE_OFF).status(JobStatus.SCHEDULED).build();
            when(jobRepository.findByJobId("job_1")).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> jobService.pauseOwned("job_1", OWNER))
                    .isInstanceOf(IllegalJobStateException.class);
        }

        @Test
        @DisplayName("resume rejects a ONE_OFF job")
        void resumeRejectsOneOff() {
            Job job = Job.builder().jobId("job_1").ownerId(OWNER).type(JobType.ONE_OFF).status(JobStatus.SCHEDULED).build();
            when(jobRepository.findByJobId("job_1")).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> jobService.resumeOwned("job_1", OWNER))
                    .isInstanceOf(IllegalJobStateException.class);
        }
    }

    @Test
    @DisplayName("resume rejects a RECURRING job that isn't currently PAUSED")
    void resumeRejectsNonPausedRecurring() {
        Job job = Job.builder().jobId("job_1").ownerId(OWNER).type(JobType.RECURRING).build();
        when(jobRepository.findByJobId("job_1")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.resumeOwned("job_1", OWNER))
                .isInstanceOf(IllegalJobStateException.class);
    }

    @Test
    @DisplayName("resume succeeds on a PAUSED RECURRING job and recomputes nextRunAt")
    void ResumeSucceedsOnPausedRecurring() {
        Job job = Job.builder().jobId("job_1").ownerId(OWNER).type(JobType.RECURRING).status(JobStatus.PAUSED).cronExpression("0 */1 * * * *").build();
        Instant recomputed = Instant.now().plusSeconds(60);
        when(jobRepository.findByJobId("job_1")).thenReturn(Optional.of(job));
        when(cronEvaluator.nextFireTime(eq("0 */1 * * * *"), any())).thenReturn(recomputed);
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        Job result = jobService.resumeOwned("job_1", OWNER);

        assertThat(result.getStatus()).isEqualTo(JobStatus.SCHEDULED);
        assertThat(result.getNextRunAt()).isEqualTo(recomputed);
    }

    @Nested
    @DisplayName("replayOwned")
    class ReplayOwned {

        @Test
        @DisplayName("delegates to DeadLetterHandler.replay for an owned job")
        void delegatesToDeadLetterHandler() {
        Job job = Job.builder().jobId("job_1").ownerId(OWNER).status(JobStatus.DEAD_LETTER).build();
        when(jobRepository.findByJobId("job_1")).thenReturn(Optional.of(job));

        jobService.replayOwned("job_1", OWNER);
        verify(deadLetterHandler).replay(job);
        }
    }

    @Test
    @DisplayName("throws JobNotFoundException, not a DeadLetterHandler call, for a job owned by someone else")
    void wrongOwnerNeverReachesHandler(){
        Job job = Job.builder().jobId("job_1").ownerId("someone_else").status(JobStatus.DEAD_LETTER).build();
        when(jobRepository.findByJobId("job_1")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.replayOwned("job_1", OWNER))
                .isInstanceOf(JobNotFoundException.class);

        verifyNoInteractions(deadLetterHandler);
    }
}


