package com.fsss.dto;

public record ErrorResponse(
        String error,
        String message
) {
}
