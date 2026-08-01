package dev.mhnuk2007.jobscheduler.job.api;

import dev.mhnuk2007.jobscheduler.config.SecurityConfig;
import dev.mhnuk2007.jobscheduler.job.api.dto.JobCreatedResponse;
import dev.mhnuk2007.jobscheduler.job.api.dto.JobResponse;
import dev.mhnuk2007.jobscheduler.job.api.mapper.JobMapper;
import dev.mhnuk2007.jobscheduler.job.domain.Job;
import dev.mhnuk2007.jobscheduler.job.domain.JobStatus;
import dev.mhnuk2007.jobscheduler.job.domain.JobType;
import dev.mhnuk2007.jobscheduler.job.exception.IllegalJobStateException;
import dev.mhnuk2007.jobscheduler.job.exception.JobNotFoundException;
import dev.mhnuk2007.jobscheduler.job.service.JobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice test for JobController. JobService and JobMapper
 * are mocked, so this tests HTTP concerns (status codes, JSON shape,
 * JWT wiring, validation, exception-to-status mapping) in isolation
 * from business logic — already covered in JobServiceImplTest.
 * <p>
 * @EnableWebSecurity is required for HttpSecurity to be available as
 * an autowire candidate for SecurityConfig.filterChain() inside this
 * narrow @WebMvcTest context — plain @Import or @ImportAutoConfiguration
 * alone are not sufficient. JwtDecoder is mocked so requests
 * authenticate via SecurityMockMvcRequestPostProcessors.jwt() without
 * needing real RSA signature verification against the on-disk key files.
 */
@WebMvcTest(JobController.class)
@ImportAutoConfiguration(SecurityConfig.class)
@EnableWebSecurity
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private JobMapper jobMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String OWNER = "demo-user";

    private Job sampleJob() {
        return Job.builder()
                .jobId("job_abc123")
                .ownerId(OWNER)
                .type(JobType.ONE_OFF)
                .status(JobStatus.SCHEDULED)
                .nextRunAt(Instant.now().plusSeconds(60))
                .maxRetries(3)
                .createdAt(Instant.now())
                .build();
    }

    private JobResponse toJobResponse(Job job) {
        return new JobResponse(
                job.getJobId(), job.getType().name(), job.getStatus(), job.getRunAt(),
                job.getCronExpression(), job.getNextRunAt(), job.getMaxRetries(), job.getCreatedAt()
        );
    }

    @Nested
    @DisplayName("POST /api/v1/jobs")
    class Submit {

        @Test
        @DisplayName("returns 201 with a valid request and valid JWT")
        void validRequest_returns201() throws Exception {
            Job job = sampleJob();
            when(jobService.submit(any(), eq(OWNER))).thenReturn(job);
            when(jobMapper.toCreatedResponse(job))
                    .thenReturn(new JobCreatedResponse(job.getJobId(), job.getStatus().name(), job.getCreatedAt()));

            String requestBody = """
                {
                    "type": "ONE_OFF",
                    "runAt": "%s",
                    "callback": {
                        "url": "https://httpbin.org/post",
                        "method": "POST"
                    }
                }
                """.formatted(Instant.now().plusSeconds(3600));

            mockMvc.perform(post("/api/v1/jobs")
                            .with(jwt().jwt(j -> j.subject(OWNER)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.jobId").value("job_abc123"));
        }

        @Test
        @DisplayName("returns 401 with no JWT")
        void noJwt_returns401() throws Exception {
            String requestBody = """
                {
                    "type": "ONE_OFF",
                    "runAt": "%s",
                    "callback": {
                        "url": "https://httpbin.org/post",
                        "method": "POST"
                    }
                }
                """.formatted(Instant.now().plusSeconds(3600));

            mockMvc.perform(post("/api/v1/jobs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(jobService);
        }

        @Test
        @DisplayName("returns 400 when @Valid rejects a missing callback")
        void missingCallback_returns400() throws Exception {
            String requestBody = """
                {
                    "type": "ONE_OFF",
                    "runAt": "%s"
                }
                """.formatted(Instant.now().plusSeconds(3600));

            mockMvc.perform(post("/api/v1/jobs")
                            .with(jwt().jwt(j -> j.subject(OWNER)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(jobService);
        }

        @Test
        @DisplayName("returns 400 on malformed JSON")
        void malformedJson_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/jobs")
                            .with(jwt().jwt(j -> j.subject(OWNER)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ not valid json"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(jobService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/jobs/{jobId}")
    class Get {

        @Test
        @DisplayName("returns 200 with job details for the owning user")
        void ownedJob_returns200() throws Exception {
            Job job = sampleJob();
            when(jobService.getOwned("job_abc123", OWNER)).thenReturn(job);
            when(jobMapper.toResponse(job)).thenReturn(toJobResponse(job));

            mockMvc.perform(get("/api/v1/jobs/job_abc123").with(jwt().jwt(j -> j.subject(OWNER))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobId").value("job_abc123"));
        }

        @Test
        @DisplayName("returns 404 for a job owned by someone else")
        void wrongOwner_returns404() throws Exception {
            when(jobService.getOwned("job_abc123", OWNER))
                    .thenThrow(new JobNotFoundException("job_abc123"));

            mockMvc.perform(get("/api/v1/jobs/job_abc123").with(jwt().jwt(j -> j.subject(OWNER))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("returns 401 with no JWT")
        void noJwt_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/jobs/job_abc123"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(jobService);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/jobs/{jobId}")
    class Cancel {

        @Test
        @DisplayName("returns 204 on successful cancel")
        void succeeds_returns204() throws Exception {
            doNothing().when(jobService).cancelOwned("job_abc123", OWNER);

            mockMvc.perform(delete("/api/v1/jobs/job_abc123").with(jwt().jwt(j -> j.subject(OWNER))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("returns 409 when the job is RUNNING")
        void runningJob_returns409() throws Exception {
            doThrow(new IllegalJobStateException("cannot cancel job currently RUNNING: job_abc123"))
                    .when(jobService).cancelOwned("job_abc123", OWNER);

            mockMvc.perform(delete("/api/v1/jobs/job_abc123").with(jwt().jwt(j -> j.subject(OWNER))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
        }

        @Test
        @DisplayName("returns 404 for a job owned by someone else")
        void wrongOwner_returns404() throws Exception {
            doThrow(new JobNotFoundException("job_abc123"))
                    .when(jobService).cancelOwned("job_abc123", OWNER);

            mockMvc.perform(delete("/api/v1/jobs/job_abc123").with(jwt().jwt(j -> j.subject(OWNER))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/jobs/{jobId}/pause")
    class Pause {

        @Test
        @DisplayName("returns 200 on success")
        void succeeds_returns200() throws Exception {
            Job paused = sampleJob();
            paused.setType(JobType.RECURRING);
            paused.setStatus(JobStatus.PAUSED);

            when(jobService.pauseOwned("job_abc123", OWNER)).thenReturn(paused);
            when(jobMapper.toResponse(paused)).thenReturn(toJobResponse(paused));

            mockMvc.perform(post("/api/v1/jobs/job_abc123/pause").with(jwt().jwt(j -> j.subject(OWNER))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAUSED"));
        }

        @Test
        @DisplayName("returns 409 for a ONE_OFF job")
        void oneOffJob_returns409() throws Exception {
            when(jobService.pauseOwned("job_abc123", OWNER))
                    .thenThrow(new IllegalJobStateException("cannot pause a ONE_OFF job: job_abc123"));

            mockMvc.perform(post("/api/v1/jobs/job_abc123/pause").with(jwt().jwt(j -> j.subject(OWNER))))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/jobs/{jobId}/resume")
    class Resume {

        @Test
        @DisplayName("returns 200 on success")
        void succeeds_returns200() throws Exception {
            Job resumed = sampleJob();
            resumed.setType(JobType.RECURRING);
            resumed.setStatus(JobStatus.SCHEDULED);
            when(jobService.resumeOwned("job_abc123", OWNER)).thenReturn(resumed);
            when(jobMapper.toResponse(resumed)).thenReturn(toJobResponse(resumed));

            mockMvc.perform(post("/api/v1/jobs/job_abc123/resume").with(jwt().jwt(j -> j.subject(OWNER))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SCHEDULED"));
        }

        @Test
        @DisplayName("returns 409 for a non-PAUSED job")
        void nonPausedJob_returns409() throws Exception {
            when(jobService.resumeOwned("job_abc123", OWNER))
                    .thenThrow(new IllegalJobStateException("cannot resume job not currently PAUSED: job_abc123"));

            mockMvc.perform(post("/api/v1/jobs/job_abc123/resume").with(jwt().jwt(j -> j.subject(OWNER))))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/jobs/{jobId}/replay")
    class Replay {

        @Test
        @DisplayName("returns 202 on success")
        void succeeds_returns202() throws Exception {
            doNothing().when(jobService).replayOwned("job_abc123", OWNER);

            mockMvc.perform(post("/api/v1/jobs/job_abc123/replay").with(jwt().jwt(j -> j.subject(OWNER))))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("returns 404 for a job owned by someone else")
        void wrongOwner_returns404() throws Exception {
            doThrow(new JobNotFoundException("job_abc123"))
                    .when(jobService).replayOwned("job_abc123", OWNER);

            mockMvc.perform(post("/api/v1/jobs/job_abc123/replay").with(jwt().jwt(j -> j.subject(OWNER))))
                    .andExpect(status().isNotFound());
        }
    }
}