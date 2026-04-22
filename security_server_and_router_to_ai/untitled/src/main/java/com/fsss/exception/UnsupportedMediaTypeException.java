package com.fsss.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedMediaTypeException extends RequestRejectedException {
    public UnsupportedMediaTypeException(String message) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }
}
