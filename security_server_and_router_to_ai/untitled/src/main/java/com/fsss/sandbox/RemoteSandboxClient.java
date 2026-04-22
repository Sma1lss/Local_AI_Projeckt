package com.fsss.sandbox;

import com.fsss.config.FsssProperties;
import com.fsss.domain.FileMetadata;
import com.fsss.service.MultipartRequestFactory;
import com.fsss.service.SpoolHandle;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Profile("edge")
public class RemoteSandboxClient implements SandboxClient {
    private static final String API_KEY_HEADER = "X-API-Key";

    private final FsssProperties properties;
    private final WebClient webClient;
    private final MultipartRequestFactory multipartRequestFactory;

    @Override
    public Mono<SandboxVerdict> scan(SpoolHandle spoolHandle, FileMetadata metadata, String detectedMime) {
        return webClient.post()
                .uri(properties.getSandbox().getRemoteUrl())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(headers -> applySecurityHeaders(headers, metadata, detectedMime))
                .body(BodyInserters.fromMultipartData(multipartRequestFactory.createFileBody(spoolHandle, metadata.sanitizedFilename())))
                .retrieve()
                .bodyToMono(SandboxResponse.class)
                .map(resp -> new SandboxVerdict(resp.verdict(), resp.findings()));
    }

    private void applySecurityHeaders(HttpHeaders headers, FileMetadata metadata, String detectedMime) {
        properties.getSecurity().getApiKeySecret().writeTo(headers, API_KEY_HEADER);
        headers.set("X-Detected-Mime", detectedMime == null ? "" : detectedMime);
        headers.set("X-Original-Filename", metadata.originalFilename());
    }
}
