package com.fsss.domain;

public record FileMetadata(
        String originalFilename,
        String sanitizedFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        String clientIp,
        String userAgent
) {
}
