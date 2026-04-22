package com.fsss.security;

import com.fsss.config.FsssProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final FsssProperties properties;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         RateLimitWebFilter rateLimitWebFilter,
                                                         InFlightLimiterWebFilter inFlightLimiterWebFilter) {
        AuthenticationWebFilter apiKeyFilter = new AuthenticationWebFilter(new ApiKeyReactiveAuthenticationManager(properties));
        apiKeyFilter.setServerAuthenticationConverter(new ApiKeyServerAuthenticationConverter());
        apiKeyFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        apiKeyFilter.setAuthenticationFailureHandler(unauthorizedHandler());
        apiKeyFilter.setAuthenticationSuccessHandler(successHandler());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/health").permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(apiKeyFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(rateLimitWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(inFlightLimiterWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    private ServerAuthenticationFailureHandler unauthorizedHandler() {
        return (exchange, ex) -> {
            exchange.getExchange().getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getExchange().getResponse().setComplete();
        };
    }

    private ServerAuthenticationSuccessHandler successHandler() {
        return (webFilterExchange, authentication) -> webFilterExchange.getChain().filter(webFilterExchange.getExchange());
    }
}
