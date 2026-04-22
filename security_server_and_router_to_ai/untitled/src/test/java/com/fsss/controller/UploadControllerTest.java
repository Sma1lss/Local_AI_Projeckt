package com.fsss.controller;

import com.fsss.domain.ScanFinding;
import com.fsss.domain.ScanVerdict;
import com.fsss.sandbox.SandboxClient;
import com.fsss.sandbox.SandboxVerdict;
import com.fsss.service.DownstreamClient;
import com.fsss.service.SpoolHandle;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("edge")
class UploadControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private SandboxClient sandboxClient;

    @MockBean
    private DownstreamClient downstreamClient;

    @Test
    void scanEndpointReturnsVerdict() {
        when(sandboxClient.scan(ArgumentMatchers.any(SpoolHandle.class), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(Mono.just(new SandboxVerdict(ScanVerdict.CLEAN,
                        List.of(new ScanFinding("tika-mime", com.fsss.scanner.ScannerOutcome.CLEAN, "ok", Map.of("detected", "text/plain"))))));
        when(downstreamClient.forward(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Mono.empty());

        String boundary = "boundary";
        String body = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\n" +
                "Content-Type: text/plain\r\n\r\n" +
                "hello\r\n--" + boundary + "--\r\n";

        webTestClient.post()
                .uri("/api/scan")
                .header("X-API-Key", "change-me")
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary))
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.verdict").isEqualTo("CLEAN");
    }
}
