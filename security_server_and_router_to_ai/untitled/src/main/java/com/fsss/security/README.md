# security

Пакет защиты входящего API.

## Что реализовано

- API key authentication (`X-API-Key` / `Authorization: Bearer ...`).
- Rate limiting по IP.
- Ограничение числа одновременных uploads.
- Resolver клиентского IP.
- Security filter chain для WebFlux.

## Классы

- `SecurityConfig`
- `ApiKeyAuthenticationToken`
- `ApiKeyReactiveAuthenticationManager`
- `ApiKeyServerAuthenticationConverter`
- `ApiKeySecret`
- `ClientIpResolver`
- `RateLimitBucket`
- `LocalRateLimitBucket`
- `RateLimiterService`
- `RateLimitWebFilter`
- `InFlightLimiterWebFilter`
