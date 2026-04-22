package com.fsss.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class ApiKeyServerAuthenticationConverter implements ServerAuthenticationConverter {
    private static final String API_KEY_HEADER = "X-API-Key";

    @Override
    public Mono<org.springframework.security.core.Authentication> convert(ServerWebExchange exchange) {
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            String bearer = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (bearer != null && bearer.startsWith("Bearer ")) {
                apiKey = bearer.substring("Bearer ".length()).trim();
            }
        }
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.empty();
        }
        return Mono.just(new ApiKeyAuthenticationToken(apiKey, false));
    }
}
