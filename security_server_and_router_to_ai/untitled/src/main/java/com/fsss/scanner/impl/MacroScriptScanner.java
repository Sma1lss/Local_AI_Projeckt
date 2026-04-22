package com.fsss.scanner.impl;

import com.fsss.config.FsssProperties;
import com.fsss.domain.ScanFinding;
import com.fsss.scanner.ScanContext;
import com.fsss.scanner.ScanHandle;
import com.fsss.scanner.ScannerOutcome;
import com.fsss.scanner.StreamingScanner;
import com.fsss.util.BytePatternScanner;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
@Order(40)
@RequiredArgsConstructor
public class MacroScriptScanner implements StreamingScanner {
    private final FsssProperties properties;

    private static final List<String> DEFAULT_PATTERNS = List.of(
            "vba",
            "vbaProject.bin",
            "autoopen",
            "document_open",
            "powershell",
            "cmd.exe",
            "wscript",
            "cscript",
            "mshta",
            "<script",
            "javascript:",
            "eval(",
            "base64"
    );

    @Override
    public String name() {
        return "macro-script";
    }

    @Override
    public ScanHandle start(ScanContext context) {
        if (!properties.getScan().getMacros().isEnabled()) {
            return new NoopHandle();
        }
        int maxBytes = properties.getScan().getMacros().getMaxScanBytes();
        BytePatternScanner scanner = new BytePatternScanner(DEFAULT_PATTERNS);

        return new ScanHandle() {
            private long scanned;
            private String matched;

            @Override
            public void accept(byte[] buffer, int offset, int length) {
                if (matched != null || scanned >= maxBytes) {
                    return;
                }
                int toScan = (int) Math.min(length, maxBytes - scanned);
                byte[] slice = new byte[toScan];
                System.arraycopy(buffer, offset, slice, 0, toScan);
                for (int i = 0; i < slice.length; i++) {
                    byte b = slice[i];
                    if (b >= 'A' && b <= 'Z') {
                        slice[i] = (byte) (b + 32);
                    }
                }
                matched = scanner.scan(slice, 0, slice.length);
                scanned += toScan;
            }

            @Override
            public Mono<ScanFinding> complete() {
                if (matched != null) {
                    return Mono.just(new ScanFinding(name(), ScannerOutcome.SUSPICIOUS,
                            "Macro/script indicator found", Map.of("pattern", matched)));
                }
                return Mono.just(new ScanFinding(name(), ScannerOutcome.CLEAN, "No macro/script indicators", Map.of()));
            }
        };
    }

    private static class NoopHandle implements ScanHandle {
        @Override
        public void accept(byte[] buffer, int offset, int length) {
        }

        @Override
        public Mono<ScanFinding> complete() {
            return Mono.just(new ScanFinding("macro-script", ScannerOutcome.SKIPPED, "Disabled", Map.of()));
        }
    }
}
