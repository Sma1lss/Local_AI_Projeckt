package com.fsss.scanner.impl;

import com.fsss.config.FsssProperties;
import com.fsss.domain.ScanFinding;
import com.fsss.domain.ScanVerdict;
import com.fsss.sandbox.DynamicSandboxResponse;
import com.fsss.scanner.PostScanScanner;
import com.fsss.scanner.ScanContext;
import com.fsss.scanner.ScannerOutcome;
import com.fsss.service.MultipartRequestFactory;
import com.fsss.service.SpoolHandle;
import com.fsss.util.DetailsMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@Order(90)
@RequiredArgsConstructor
@Profile("sandbox")
public class DynamicSandboxScanner implements PostScanScanner {
    private static final String API_KEY_HEADER = "X-API-Key";

    private final FsssProperties properties;
    private final WebClient webClient;
    private final MultipartRequestFactory multipartRequestFactory;

    @Override
    public String name() {
        return "dynamic-sandbox";
    }

    @Override
    public Mono<ScanFinding> scan(ScanContext context, SpoolHandle spoolHandle, String detectedMime) {
        if (!properties.getScan().getDynamic().isEnabled()) {
            return Mono.just(new ScanFinding(name(), ScannerOutcome.SKIPPED, "Disabled", Map.of()));
        }

        return webClient.post()
                .uri(properties.getScan().getDynamic().getUrl())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(this::applySecurityHeaders)
                .body(BodyInserters.fromMultipartData(multipartRequestFactory.createFileBody(spoolHandle, context.metadata().sanitizedFilename())))
                .retrieve()
                .bodyToMono(DynamicSandboxResponse.class)
                .map(resp -> {
                    if (resp.verdict() == ScanVerdict.MALICIOUS) {
                        return new ScanFinding(name(), ScannerOutcome.MALICIOUS, "Dynamic analysis detected threat",
                                DetailsMap.create().add("details", resp.details()).build());
                    }
                    if (resp.verdict() == ScanVerdict.SUSPICIOUS) {
                        return new ScanFinding(name(), ScannerOutcome.SUSPICIOUS, "Dynamic analysis suspicious",
                                DetailsMap.create().add("details", resp.details()).build());
                    }
                    return new ScanFinding(name(), ScannerOutcome.CLEAN, "Dynamic analysis clean", Map.of());
                })
                .onErrorResume(ex -> Mono.just(new ScanFinding(
                        name(),
                        ScannerOutcome.ERROR,
                        "Dynamic analysis failed",
                        DetailsMap.create().add("error", ex.getMessage()).build()
                )));
    }

    private void applySecurityHeaders(HttpHeaders headers) {
        properties.getSecurity().getApiKeySecret().writeTo(headers, API_KEY_HEADER);
    }
}
