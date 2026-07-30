package dev.mhnuk2007.jobscheduler.job.api;

import dev.mhnuk2007.jobscheduler.job.api.dto.JobCreatedResponse;
import dev.mhnuk2007.jobscheduler.job.api.dto.JobResponse;
import dev.mhnuk2007.jobscheduler.job.api.dto.JobRunsResponse;
import dev.mhnuk2007.jobscheduler.job.api.dto.JobSubmitRequest;
import dev.mhnuk2007.jobscheduler.job.api.mapper.JobMapper;
import dev.mhnuk2007.jobscheduler.job.domain.Job;
import dev.mhnuk2007.jobscheduler.job.domain.JobRun;
import dev.mhnuk2007.jobscheduler.job.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;
    private final JobMapper jobMapper;

    public JobController(JobService jobService, JobMapper jobMapper) {
        this.jobService = jobService;
        this.jobMapper = jobMapper;
    }

    @PostMapping
    public ResponseEntity<JobCreatedResponse> submit(
            @Valid @RequestBody JobSubmitRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Job job = jobService.submit(request, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(jobMapper.toCreatedResponse(job));
    }

    @GetMapping("/{jobId}")
    public JobResponse get(@PathVariable String jobId, @AuthenticationPrincipal Jwt jwt) {
        Job job = jobService.getOwned(jobId, jwt.getSubject());
        return jobMapper.toResponse(job);
    }

    @GetMapping("/{jobId}/runs")
    public JobRunsResponse getRuns(@PathVariable String jobId, @AuthenticationPrincipal Jwt jwt) {
        List<JobRun> runs = jobService.getRunsOwned(jobId, jwt.getSubject());
        return jobMapper.toRunsResponse(jobId, runs);
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> cancel(@PathVariable String jobId, @AuthenticationPrincipal Jwt jwt) {
        jobService.cancelOwned(jobId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}