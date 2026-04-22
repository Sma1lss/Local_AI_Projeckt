package com.fsss.controller;

import com.fsss.sandbox.DynamicSandboxResponse;
import com.fsss.sandbox.SandboxResponse;
import com.fsss.service.SandboxScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Profile("sandbox")
public class SandboxController {
    private final SandboxScanService sandboxScanService;

    @PostMapping("/scan")
    public Mono<SandboxResponse> scan(ServerWebExchange exchange) {
        return sandboxScanService.scan(exchange);
    }

    @PostMapping("/dynamic")
    public Mono<DynamicSandboxResponse> dynamic(ServerWebExchange exchange) {
        return Mono.just(new DynamicSandboxResponse(com.fsss.domain.ScanVerdict.CLEAN, "dynamic analysis not configured"));
    }
}
