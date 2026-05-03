# Edge Service Guide

Профиль: `edge`
Основной endpoint: `POST /api/scan`
Порт по умолчанию: `8080`

## Роль микросервиса

`edge` - это внешний защищенный upload gateway. Он принимает файл от клиента, валидирует и спулит поток, передает файл в sandbox-сервис, а затем clean-файл отправляет downstream-сервису.

## Основной flow

1. `SecurityConfig` пропускает запрос только с валидным API key.
2. `RateLimitWebFilter` ограничивает частоту по IP.
3. `InFlightLimiterWebFilter` ограничивает число одновременных upload'ов.
4. `UploadController.scan()` передает запрос в `FileScanService`.
5. `EdgeStreamProcessor` разбирает multipart stream и формирует `EdgeProcessingResult`.
6. `RemoteSandboxClient` отправляет spool в `sandbox` profile.
7. `FileScanService.handleVerdict()`
   - при `CLEAN` вызывает `DownstreamClient.forward()`;
   - при `MALICIOUS/SUSPICIOUS/ERROR` пишет security event и не форвардит файл.
8. spool безопасно удаляется.

## Критичные классы

- `UploadController`
- `FileScanService`
- `EdgeStreamProcessor`
- `RemoteSandboxClient`
- `DownstreamClient`
- `SecurityEventLogger`
- `SecurityConfig`

## Что уже готово

- защищенный API ingress;
- потоковый прием multipart без полного чтения в память;
- sha256, sanitized filename, client IP capture;
- sandbox delegation;
- downstream forwarding только для clean-файлов.

## Что в процессе разработки

- выравнивание downstream API с `webServer`;
- retry/persistence для forwarding;
- richer observability по цепочке edge -> sandbox -> downstream.

## Что важно разработчику и AI-агенту

- Любая ошибка в `EdgeStreamProcessor` способна превратить upload-path в DoS/vector.
- Нельзя ослаблять security filters без отдельной ревизии.
- Контракт `POST /api/scan` должен оставаться стабильным для внешних клиентов.
