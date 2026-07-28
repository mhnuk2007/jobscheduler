package dev.mhnuk2007.jobscheduler.job.exception;

public class IllegalJobStateException extends RuntimeException{
    public IllegalJobStateException(String message) {
        super(message);
    }
}
