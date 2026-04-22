package com.fsss.service;

import com.fsss.domain.ScanReport;

public record ScanProcessingResult(
        String scanId,
        ScanReport report,
        SpoolHandle spoolHandle
) {
}
