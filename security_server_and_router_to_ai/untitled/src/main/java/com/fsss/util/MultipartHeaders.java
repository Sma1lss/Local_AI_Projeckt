package com.fsss.util;

public record MultipartHeaders(
        String name,
        String filename,
        String contentType
) {
}
