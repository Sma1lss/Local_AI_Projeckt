package com.fsss.service;

import com.fsss.config.FsssProperties;
import com.fsss.domain.FileMetadata;
import com.fsss.domain.ScanReport;
import com.fsss.exception.FileTooLargeException;
import com.fsss.exception.MultipartParsingException;
import com.fsss.exception.RequestRejectedException;
import com.fsss.exception.UnsupportedMediaTypeException;
import com.fsss.logging.SecurityEventLogger;
import com.fsss.scanner.ScanContext;
import com.fsss.scanner.ScannerPipeline;
import com.fsss.util.FilenameSanitizer;
import com.fsss.util.Hex;
import com.fsss.util.MultipartHeaders;
import com.fsss.util.MultipartStreamConsumer;
import com.fsss.util.MultipartStreamingParser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;

import java.io.OutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Profile("sandbox")
public class StreamingScanProcessor {
    private final MultipartStreamingParser multipartStreamingParser;
    private final ScannerPipeline scannerPipeline;
    private final Spooler spooler;
    private final FsssProperties properties;
    private final Scheduler scanScheduler;
    private final SecurityEventLogger securityEventLogger;
    private final ScanFindingAnalyzer scanFindingAnalyzer;

    public Mono<ScanProcessingResult> process(ServerWebExchange exchange) {
        String contentType = exchange.getRequest().getHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            return Mono.error(new UnsupportedMediaTypeException("Content-Type must be multipart/form-data"));
        }
        String scanId = UUID.randomUUID().toString();
        ScanMultipartConsumer consumer = new ScanMultipartConsumer(scanId, exchange);
        return multipartStreamingParser.parse(exchange.getRequest().getBody().publishOn(scanScheduler), contentType, consumer)
                .doOnError(ex -> consumer.cleanup())
                .then(consumer.result());
    }

    private final class ScanMultipartConsumer implements MultipartStreamConsumer {
        private final String scanId;
        private final ServerWebExchange exchange;
        private final Sinks.One<ScanProcessingResult> sink = Sinks.one();
        private OutputStream spoolOut;
        private SpoolHandle spoolHandle;
        private ScannerPipeline.ScanSession scanSession;
        private MessageDigest sha256;
        private long size;
        private FileMetadata metadata;
        private Instant start;

        private ScanMultipartConsumer(String scanId, ServerWebExchange exchange) {
            this.scanId = scanId;
            this.exchange = exchange;
        }

        @Override
        public void onHeaders(MultipartHeaders headers) throws Exception {
            if (!"file".equals(headers.name())) {
                throw new MultipartParsingException("Multipart part 'file' is required");
            }
            if (headers.filename() == null || headers.filename().isBlank()) {
                throw new MultipartParsingException("Filename is required");
            }
            String sanitized = FilenameSanitizer.sanitize(headers.filename());
            if (FilenameSanitizer.isSuspicious(headers.filename())) {
                securityEventLogger.suspiciousActivity(Map.of(
                        "event", "suspicious_filename",
                        "filename", headers.filename(),
                        "scanId", scanId
                ));
            }
            spoolHandle = spooler.create(scanId);
            spoolOut = spoolHandle.outputStream();
            sha256 = MessageDigest.getInstance("SHA-256");
            start = Instant.now();
            metadata = new FileMetadata(
                    headers.filename(),
                    sanitized,
                    headers.contentType(),
                    0,
                    "",
                    exchange.getRequest().getRemoteAddress() != null
                            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                            : "unknown",
                    exchange.getRequest().getHeaders().getFirst("User-Agent")
            );
            scanSession = scannerPipeline.startSession(new ScanContext(scanId, metadata, start));
        }

        @Override
        public void onData(byte[] buffer, int offset, int length) throws Exception {
            size += length;
            if (size > properties.getMaxFileSizeBytes()) {
                throw new FileTooLargeException("File exceeds max size");
            }
            sha256.update(buffer, offset, length);
            spoolOut.write(buffer, offset, length);
            scanSession.accept(buffer, offset, length);
        }

        @Override
        public void onPartEnd() throws Exception {
            spoolHandle.seal();
            FileMetadata finalMeta = new FileMetadata(
                    metadata.originalFilename(),
                    metadata.sanitizedFilename(),
                    metadata.contentType(),
                    size,
                    Hex.toHex(sha256.digest()),
                    metadata.clientIp(),
                    metadata.userAgent()
            );

            scanSession.complete()
                    .subscribeOn(scanScheduler)
                    .map(findings -> new ScanProcessingResult(
                            scanId,
                            new ScanReport(
                                    scanFindingAnalyzer.resolveVerdict(findings),
                                    scanFindingAnalyzer.resolveDetectedMime(findings),
                                    metadata.contentType(),
                                    finalMeta,
                                    findings,
                                    Duration.between(start, Instant.now())
                            ),
                            spoolHandle
                    ))
                    .doOnError(err -> spoolHandle.secureDelete())
                    .subscribe(
                            result -> sink.tryEmitValue(result),
                            error -> sink.tryEmitError(new RequestRejectedException(HttpStatus.INTERNAL_SERVER_ERROR, error.getMessage()))
                    );
        }

        private Mono<ScanProcessingResult> result() {
            return sink.asMono();
        }

        private void cleanup() {
            if (spoolHandle != null) {
                spoolHandle.secureDelete();
            }
        }
    }
}
