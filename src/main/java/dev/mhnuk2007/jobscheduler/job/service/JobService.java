package dev.mhnuk2007.jobscheduler.job.service;

import dev.mhnuk2007.jobscheduler.job.api.dto.JobSubmitRequest;
import dev.mhnuk2007.jobscheduler.job.domain.Job;
import dev.mhnuk2007.jobscheduler.job.domain.JobRun;

import java.util.List;

public interface JobService {
    Job submit(JobSubmitRequest request, String ownerId);
    Job getOwned(String jobId, String ownerId);
    List<JobRun> getRunsOwned(String jobId, String ownerId);
    void cancelOwned(String jobId, String ownerId);
    Job pauseOwned(String jobId, String ownerId);
    Job resumeOwned(String jobId, String ownerId);
    void replayOwned(String jobId, String ownerId);
}
