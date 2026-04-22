package com.fsss.scanner;

import com.fsss.domain.FileMetadata;

import java.time.Instant;

public record ScanContext(
        String scanId,
        FileMetadata metadata,
        Instant startedAt
) {
}
