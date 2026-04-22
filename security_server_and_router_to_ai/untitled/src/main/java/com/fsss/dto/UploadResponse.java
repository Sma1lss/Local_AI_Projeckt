package com.fsss.dto;

import com.fsss.domain.ScanFinding;
import com.fsss.domain.ScanVerdict;

import java.util.List;

public record UploadResponse(
        String scanId,
        ScanVerdict verdict,
        String sha256,
        long sizeBytes,
        String detectedMime,
        List<ScanFinding> findings
) {
}
