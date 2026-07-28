package dev.mhnuk2007.jobscheduler.job.exceptions;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String jobId) {
        super("Could not find job with id " + jobId);
    }
}
