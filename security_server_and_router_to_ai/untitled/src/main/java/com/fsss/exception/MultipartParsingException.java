package com.fsss.exception;

import org.springframework.http.HttpStatus;

public class MultipartParsingException extends RequestRejectedException {
    public MultipartParsingException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
