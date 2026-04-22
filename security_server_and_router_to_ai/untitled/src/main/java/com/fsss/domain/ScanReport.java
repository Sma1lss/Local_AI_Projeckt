package com.fsss.domain;

import java.time.Duration;
import java.util.List;

public record ScanReport(
        ScanVerdict verdict,
        String detectedMime,
        String declaredMime,
        FileMetadata metadata,
        List<ScanFinding> findings,
        Duration duration
) {
}
