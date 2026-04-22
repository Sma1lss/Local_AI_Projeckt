package com.fsss.scanner.impl;

import com.fsss.config.FsssProperties;
import com.fsss.domain.ScanFinding;
import com.fsss.scanner.ScanContext;
import com.fsss.scanner.ScanHandle;
import com.fsss.scanner.ScannerOutcome;
import com.fsss.scanner.StreamingScanner;
import com.fsss.util.BytePatternScanner;
import com.fsss.util.EntropyCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@Order(20)
@RequiredArgsConstructor
public class EntropySignatureScanner implements StreamingScanner {
    private static final double HIGH_ENTROPY_THRESHOLD = 7.5;
    private final FsssProperties properties;

    @Override
    public String name() {
        return "entropy-signature";
    }

    @Override
    public ScanHandle start(ScanContext context) {
        boolean signaturesEnabled = properties.getScan().getSignatures().isEnabled();
        BytePatternScanner patternScanner = signaturesEnabled
                ? new BytePatternScanner(properties.getScan().getSignatures().getPatterns())
                : null;
        EntropyCalculator entropy = new EntropyCalculator();

        return new ScanHandle() {
            private String matchedPattern;

            @Override
            public void accept(byte[] buffer, int offset, int length) {
                entropy.update(buffer, offset, length);
                if (patternScanner != null && matchedPattern == null) {
                    matchedPattern = patternScanner.scan(buffer, offset, length);
                }
            }

            @Override
            public Mono<ScanFinding> complete() {
                double entropyValue = entropy.entropy();
                if (matchedPattern != null) {
                    return Mono.just(new ScanFinding(name(), ScannerOutcome.MALICIOUS,
                            "Signature matched", Map.of("pattern", matchedPattern)));
                }
                if (entropyValue >= HIGH_ENTROPY_THRESHOLD) {
                    return Mono.just(new ScanFinding(name(), ScannerOutcome.SUSPICIOUS,
                            "High entropy content", Map.of("entropy", entropyValue)));
                }
                return Mono.just(new ScanFinding(name(), ScannerOutcome.CLEAN, "Entropy OK", Map.of("entropy", entropyValue)));
            }
        };
    }
}
