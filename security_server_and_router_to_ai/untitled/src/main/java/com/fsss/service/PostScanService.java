package com.fsss.service;

import com.fsss.domain.ScanFinding;
import com.fsss.scanner.PostScanScanner;
import com.fsss.scanner.ScanContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PostScanService {
    private final List<PostScanScanner> scanners;

    public Mono<List<ScanFinding>> run(ScanContext context, SpoolHandle spoolHandle, String detectedMime) {
        if (scanners == null || scanners.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(scanners)
                .concatMap(scanner -> scanner.scan(context, spoolHandle, detectedMime))
                .collectList();
    }
}
