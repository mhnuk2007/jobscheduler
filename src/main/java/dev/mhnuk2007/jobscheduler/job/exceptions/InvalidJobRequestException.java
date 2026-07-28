package dev.mhnuk2007.jobscheduler.job.exceptions;

public class InvalidJobRequestException extends RuntimeException{
    public InvalidJobRequestException(String message){
        super(message);
    }
}
