package com.fsss.service;

import com.fsss.domain.ScanVerdict;
import com.fsss.dto.UploadResponse;
import com.fsss.dto.UploadResponseMapper;
import com.fsss.exception.RequestRejectedException;
import com.fsss.exception.ScanFailedException;
import com.fsss.logging.SecurityEventLogger;
import com.fsss.sandbox.SandboxClient;
import com.fsss.sandbox.SandboxVerdict;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Profile("edge")
public class FileScanService {
    private final EdgeStreamProcessor edgeStreamProcessor;
    private final SandboxClient sandboxClient;
    private final DownstreamClient downstreamClient;
    private final SecurityEventLogger securityEventLogger;
    private final UploadResponseMapper mapper;
    private final ScanFindingAnalyzer scanFindingAnalyzer;

    public Mono<UploadResponse> scanAndForward(ServerWebExchange exchange) {
        return edgeStreamProcessor.process(exchange)
                .flatMap(result -> sandboxClient.scan(result.spoolHandle(), result.metadata(), "")
                        .flatMap(verdict -> handleVerdict(result, verdict)))
                .onErrorResume(ex -> {
                    if (ex instanceof RequestRejectedException) {
                        return Mono.error(ex);
                    }
                    return Mono.error(new ScanFailedException(ex.getMessage()));
                });
    }

    private Mono<UploadResponse> handleVerdict(EdgeProcessingResult result, SandboxVerdict verdict) {
        ScanVerdict finalVerdict = verdict.verdict();
        Mono<UploadResponse> responseMono = Mono.fromCallable(() ->
                mapper.toResponse(
                        result.scanId(),
                        finalVerdict,
                        result.metadata(),
                        scanFindingAnalyzer.resolveDetectedMime(verdict.findings()),
                        verdict.findings()
                )
        );

        if (finalVerdict == ScanVerdict.CLEAN) {
            return downstreamClient.forward(result.spoolHandle(), result.metadata())
                    .then(responseMono)
                    .doFinally(signal -> result.spoolHandle().secureDelete());
        }
        if (finalVerdict == ScanVerdict.MALICIOUS || finalVerdict == ScanVerdict.SUSPICIOUS) {
            securityEventLogger.threatDetected(createSecurityPayload(result, finalVerdict, verdict));
        } else if (finalVerdict == ScanVerdict.ERROR) {
            securityEventLogger.suspiciousActivity(createSecurityPayload(result, finalVerdict, verdict));
        }
        result.spoolHandle().secureDelete();
        return responseMono;
    }

    private Map<String, Object> createSecurityPayload(EdgeProcessingResult result, ScanVerdict verdict, SandboxVerdict sandboxVerdict) {
        return Map.of(
                "scanId", result.scanId(),
                "ip", result.metadata().clientIp(),
                "hash", result.metadata().sha256(),
                "verdict", verdict,
                "findings", sandboxVerdict.findings()
        );
    }
}
