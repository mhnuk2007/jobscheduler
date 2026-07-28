package dev.mhnuk2007.jobscheduler.job.exceptions;

public class IllegalJobStateException extends RuntimeException{
    public IllegalJobStateException(String message) {
        super(message);
    }
}
