package com.fsss.service;

import com.fsss.config.FsssProperties;
import com.fsss.domain.FileMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class DownstreamClient {
    private final FsssProperties properties;
    private final WebClient webClient;
    private final MultipartRequestFactory multipartRequestFactory;

    public Mono<Void> forward(SpoolHandle spoolHandle, FileMetadata metadata) {
        return webClient.post()
                .uri(properties.getDownstream().getUrl())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header("X-File-Sha256", metadata.sha256())
                .header("X-Original-Filename", metadata.originalFilename())
                .body(BodyInserters.fromMultipartData(multipartRequestFactory.createFileBody(spoolHandle, metadata.sanitizedFilename())))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(properties.getDownstream().getTimeout());
    }
}
