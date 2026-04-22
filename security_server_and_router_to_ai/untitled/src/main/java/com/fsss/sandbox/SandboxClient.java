package com.fsss.sandbox;

import com.fsss.domain.FileMetadata;
import com.fsss.service.SpoolHandle;
import reactor.core.publisher.Mono;

public interface SandboxClient {
    Mono<SandboxVerdict> scan(SpoolHandle spoolHandle, FileMetadata metadata, String detectedMime);
}
