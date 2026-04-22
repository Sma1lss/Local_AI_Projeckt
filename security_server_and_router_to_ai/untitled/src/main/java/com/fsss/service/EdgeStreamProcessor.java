package com.fsss.service;

import com.fsss.config.FsssProperties;
import com.fsss.domain.FileMetadata;
import com.fsss.exception.FileTooLargeException;
import com.fsss.exception.MultipartParsingException;
import com.fsss.exception.UnsupportedMediaTypeException;
import com.fsss.logging.SecurityEventLogger;
import com.fsss.security.ClientIpResolver;
import com.fsss.util.FilenameSanitizer;
import com.fsss.util.Hex;
import com.fsss.util.MultipartHeaders;
import com.fsss.util.MultipartStreamConsumer;
import com.fsss.util.MultipartStreamingParser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;

import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Profile("edge")
public class EdgeStreamProcessor {
    private final MultipartStreamingParser multipartStreamingParser;
    private final Spooler spooler;
    private final FsssProperties properties;
    private final Scheduler scanScheduler;
    private final SecurityEventLogger securityEventLogger;
    private final ClientIpResolver clientIpResolver;

    public Mono<EdgeProcessingResult> process(ServerWebExchange exchange) {
        String contentType = exchange.getRequest().getHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            return Mono.error(new UnsupportedMediaTypeException("Content-Type must be multipart/form-data"));
        }
        String scanId = UUID.randomUUID().toString();
        EdgeMultipartConsumer consumer = new EdgeMultipartConsumer(scanId, exchange);
        return multipartStreamingParser.parse(exchange.getRequest().getBody().publishOn(scanScheduler), contentType, consumer)
                .doOnError(ex -> consumer.cleanup())
                .then(consumer.result());
    }

    private class EdgeMultipartConsumer implements MultipartStreamConsumer {
        private final String scanId;
        private final ServerWebExchange exchange;
        private final Sinks.One<EdgeProcessingResult> sink = Sinks.one();
        private OutputStream spoolOut;
        private SpoolHandle spoolHandle;
        private MessageDigest sha256;
        private long size;
        private FileMetadata metadata;

        private EdgeMultipartConsumer(String scanId, ServerWebExchange exchange) {
            this.scanId = scanId;
            this.exchange = exchange;
        }

        @Override
        public void onHeaders(MultipartHeaders headers) throws Exception {
            if (headers.name() == null || !headers.name().equals("file")) {
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
            this.spoolHandle = spooler.create(scanId);
            this.spoolOut = spoolHandle.outputStream();
            this.sha256 = MessageDigest.getInstance("SHA-256");

            this.metadata = new FileMetadata(
                    headers.filename(),
                    sanitized,
                    headers.contentType(),
                    0,
                    "",
                    clientIpResolver.resolve(exchange),
                    exchange.getRequest().getHeaders().getFirst("User-Agent")
            );
        }

        @Override
        public void onData(byte[] buffer, int offset, int length) throws Exception {
            size += length;
            if (size > properties.getMaxFileSizeBytes()) {
                throw new FileTooLargeException("File exceeds max size");
            }
            sha256.update(buffer, offset, length);
            spoolOut.write(buffer, offset, length);
        }

        @Override
        public void onPartEnd() throws Exception {
            spoolHandle.seal();
            String hash = Hex.toHex(sha256.digest());
            FileMetadata finalMeta = new FileMetadata(
                    metadata.originalFilename(),
                    metadata.sanitizedFilename(),
                    metadata.contentType(),
                    size,
                    hash,
                    metadata.clientIp(),
                    metadata.userAgent()
            );
            sink.tryEmitValue(new EdgeProcessingResult(scanId, finalMeta, spoolHandle));
        }

        public Mono<EdgeProcessingResult> result() {
            return sink.asMono();
        }

        public void cleanup() {
            if (spoolHandle != null) {
                spoolHandle.secureDelete();
            }
        }
    }
}
