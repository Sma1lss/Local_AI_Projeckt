package com.fsss.service;

import com.fsss.domain.FileMetadata;

public record EdgeProcessingResult(
        String scanId,
        FileMetadata metadata,
        SpoolHandle spoolHandle
) {
}
