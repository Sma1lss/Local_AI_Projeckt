package com.fsss.security;

import com.fsss.logging.SecurityEventLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RateLimitWebFilter implements WebFilter {
    private final RateLimiterService rateLimiterService;
    private final ClientIpResolver clientIpResolver;
    private final SecurityEventLogger securityEventLogger;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ip = clientIpResolver.resolve(exchange);
        RateLimitBucket bucket = rateLimiterService.resolveBucket(ip);
        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        }
        securityEventLogger.rateLimitExceeded(ip, exchange.getRequest().getPath().value());
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return exchange.getResponse().setComplete();
    }
}
