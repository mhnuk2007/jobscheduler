package dev.mhnuk2007.jobscheduler.job.exception;

public class InvalidJobRequestException extends RuntimeException{
    public InvalidJobRequestException(String message){
        super(message);
    }
}
