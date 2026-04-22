package com.fsss.service;

import com.fsss.domain.ScanFinding;
import com.fsss.domain.ScanReport;
import com.fsss.metrics.ScanMetrics;
import com.fsss.scanner.ScanContext;
import com.fsss.sandbox.SandboxResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Profile("sandbox")
public class SandboxScanService {
    private final StreamingScanProcessor scanProcessor;
    private final PostScanService postScanService;
    private final ScanMetrics metrics;
    private final ScanFindingAnalyzer scanFindingAnalyzer;

    public Mono<SandboxResponse> scan(ServerWebExchange exchange) {
        return scanProcessor.process(exchange)
                .flatMap(result -> {
                    ScanReport report = result.report();
                    ScanContext context = new ScanContext(result.scanId(), report.metadata(), Instant.now());
                    return postScanService.run(context, result.spoolHandle(), report.detectedMime())
                            .map(postFindings -> buildResponse(report, postFindings))
                            .doOnNext(response -> metrics.record(response.verdict().name(), report.duration()))
                            .doFinally(signal -> result.spoolHandle().secureDelete());
                });
    }

    private SandboxResponse buildResponse(ScanReport report, List<ScanFinding> postFindings) {
        List<ScanFinding> mergedFindings = scanFindingAnalyzer.merge(report.findings(), postFindings);
        return new SandboxResponse(scanFindingAnalyzer.resolveVerdict(mergedFindings), mergedFindings);
    }
}
