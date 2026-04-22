package com.fsss.security;

import com.fsss.config.FsssProperties;
import com.fsss.logging.SecurityEventLogger;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.Semaphore;

@Component
@RequiredArgsConstructor
public class InFlightLimiterWebFilter implements WebFilter {
    private final FsssProperties properties;
    private final SecurityEventLogger securityEventLogger;

    private Semaphore semaphore;

    @PostConstruct
    void init() {
        this.semaphore = new Semaphore(properties.getSecurity().getMaxInFlight(), true);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        boolean acquired = semaphore.tryAcquire();
        if (!acquired) {
            securityEventLogger.tooManyConcurrentUploads(exchange.getRequest().getPath().value());
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange)
                .doFinally(signalType -> semaphore.release());
    }
}
