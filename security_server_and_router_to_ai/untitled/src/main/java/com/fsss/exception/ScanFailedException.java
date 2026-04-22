package com.fsss.exception;

import org.springframework.http.HttpStatus;

public class ScanFailedException extends RequestRejectedException {
    public ScanFailedException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
