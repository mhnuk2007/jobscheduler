package dev.mhnuk2007.jobscheduler.security;

public interface CallbackUrlValidator {
    boolean isAllowed(String url);
}
