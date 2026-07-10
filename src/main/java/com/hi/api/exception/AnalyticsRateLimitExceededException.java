package com.hi.api.exception;

public class AnalyticsRateLimitExceededException extends RuntimeException {
    public AnalyticsRateLimitExceededException() {
        super("Analytics rate limit exceeded");
    }
}
