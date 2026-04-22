package com.fsss.scanner.impl;

import com.fsss.config.FsssProperties;
import com.fsss.domain.ScanFinding;
import com.fsss.scanner.PostScanScanner;
import com.fsss.scanner.ScanContext;
import com.fsss.scanner.ScannerOutcome;
import com.fsss.service.SpoolHandle;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
@Order(80)
@RequiredArgsConstructor
@Profile("sandbox")
public class YaraPostScanner implements PostScanScanner {
    private final FsssProperties properties;

    @Override
    public String name() {
        return "yara";
    }

    @Override
    public Mono<ScanFinding> scan(ScanContext context, SpoolHandle spoolHandle, String detectedMime) {
        if (!properties.getScan().getYara().isEnabled()) {
            return Mono.just(new ScanFinding(name(), ScannerOutcome.SKIPPED, "Disabled", Map.of()));
        }
        Path rules = Path.of(properties.getScan().getYara().getRulesPath());
        if (!Files.exists(rules)) {
            return Mono.just(new ScanFinding(name(), ScannerOutcome.ERROR, "Rules file missing", Map.of("rules", rules.toString())));
        }
        return Mono.fromCallable(() -> {
            Path file = spoolHandle.path();
            Path temp = null;
            if (file == null) {
                temp = Files.createTempFile(Path.of(properties.getSpool().getTempDir()), "yara-", ".bin");
                try (InputStream in = spoolHandle.openInputStream()) {
                    Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                file = temp;
            }
            Process process = new ProcessBuilder("yara", rules.toString(), file.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (temp != null) {
                Files.deleteIfExists(temp);
            }
            if (exit == 0 && !output.isBlank()) {
                return new ScanFinding(name(), ScannerOutcome.MALICIOUS, "YARA match", Map.of("matches", output.trim()));
            }
            if (exit == 1 || output.isBlank()) {
                return new ScanFinding(name(), ScannerOutcome.CLEAN, "No YARA matches", Map.of());
            }
            return new ScanFinding(name(), ScannerOutcome.ERROR, "YARA error", Map.of("output", output.trim()));
        });
    }
}
