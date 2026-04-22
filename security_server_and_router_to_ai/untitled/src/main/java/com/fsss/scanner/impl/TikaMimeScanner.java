package com.fsss.scanner.impl;

import com.fsss.config.FsssProperties;
import com.fsss.domain.ScanFinding;
import com.fsss.scanner.ScanContext;
import com.fsss.scanner.ScanHandle;
import com.fsss.scanner.ScannerOutcome;
import com.fsss.scanner.StreamingScanner;
import com.fsss.util.DetailsMap;
import lombok.RequiredArgsConstructor;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;

@Component
@Order(10)
@RequiredArgsConstructor
public class TikaMimeScanner implements StreamingScanner {
    private final FsssProperties properties;
    private final DefaultDetector detector = new DefaultDetector();

    @Override
    public String name() {
        return "tika-mime";
    }

    @Override
    public ScanHandle start(ScanContext context) {
        int maxBytes = properties.getScan().getMime().getMaxBytes();
        return new ScanHandle() {
            private final byte[] buffer = new byte[maxBytes];
            private int pos;

            @Override
            public void accept(byte[] data, int offset, int length) {
                int remaining = maxBytes - pos;
                if (remaining <= 0) {
                    return;
                }
                int toCopy = Math.min(remaining, length);
                System.arraycopy(data, offset, buffer, pos, toCopy);
                pos += toCopy;
            }

            @Override
            public Mono<ScanFinding> complete() {
                String declared = context.metadata().contentType();
                MediaType detected;
                try (ByteArrayInputStream is = new ByteArrayInputStream(buffer, 0, pos)) {
                    detected = detector.detect(is, new Metadata());
                } catch (Exception e) {
                    return Mono.just(new ScanFinding(
                            name(),
                            ScannerOutcome.ERROR,
                            "Tika detection failed",
                            DetailsMap.create().add("error", e.getMessage()).build()
                    ));
                }
                String detectedValue = detected != null ? detected.toString() : "unknown";
                boolean mismatch = declared != null && !declared.isBlank() && !detectedValue.equalsIgnoreCase(declared);
                return Mono.just(new ScanFinding(
                        name(),
                        mismatch ? ScannerOutcome.SUSPICIOUS : ScannerOutcome.CLEAN,
                        mismatch ? "Declared MIME does not match detected" : "MIME OK",
                        DetailsMap.create().add("declared", declared).add("detected", detectedValue).build()
                ));
            }
        };
    }
}
