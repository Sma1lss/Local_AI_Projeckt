package com.fsss.exception;

import org.springframework.http.HttpStatus;

public class RequestRejectedException extends RuntimeException {
    private final HttpStatus status;

    public RequestRejectedException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
