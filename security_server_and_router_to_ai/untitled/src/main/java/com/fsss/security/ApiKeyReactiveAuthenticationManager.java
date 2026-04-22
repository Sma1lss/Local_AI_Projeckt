package com.fsss.security;

import com.fsss.config.FsssProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ApiKeyReactiveAuthenticationManager implements ReactiveAuthenticationManager {
    private final FsssProperties properties;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) throws AuthenticationException {
        String apiKey = authentication.getCredentials() instanceof String credentials ? credentials : null;
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.empty();
        }
        if (properties.getSecurity().getApiKeySecret().matches(apiKey)) {
            return Mono.just(new ApiKeyAuthenticationToken(apiKey, true));
        }
        return Mono.empty();
    }
}
