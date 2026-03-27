package com.joshfouchey.smsarchive.exception;

public class JobAlreadyRunningException extends RuntimeException {
    public JobAlreadyRunningException(String message) {
        super(message);
    }
}
