package com.fsss.exception;

import org.springframework.http.HttpStatus;

public class FileTooLargeException extends RequestRejectedException {
    public FileTooLargeException(String message) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }
}
