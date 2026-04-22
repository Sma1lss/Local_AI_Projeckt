package com.fsss.controller;

import com.fsss.dto.UploadResponse;
import com.fsss.service.FileScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Profile("edge")
public class UploadController {
    private final FileScanService fileScanService;

    @PostMapping("/scan")
    public Mono<UploadResponse> scan(ServerWebExchange exchange) {
        return fileScanService.scanAndForward(exchange);
    }
}
