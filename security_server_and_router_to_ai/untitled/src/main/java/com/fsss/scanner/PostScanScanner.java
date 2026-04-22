package com.fsss.scanner;

import com.fsss.domain.ScanFinding;
import com.fsss.service.SpoolHandle;
import reactor.core.publisher.Mono;

public interface PostScanScanner {
    String name();

    Mono<ScanFinding> scan(ScanContext context, SpoolHandle spoolHandle, String detectedMime);
}
