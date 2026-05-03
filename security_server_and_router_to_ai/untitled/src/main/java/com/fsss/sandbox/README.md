# sandbox

Контракты и клиент для связи между `edge` и `sandbox` профилями.

## Классы

- `SandboxClient` - интерфейс вызова sandbox из edge.
- `RemoteSandboxClient` - HTTP-реализация вызова `POST /internal/scan`.
- `SandboxResponse` - ответ sandbox-контроллера.
- `SandboxVerdict` - внутреннее представление sandbox-вердикта в edge.
- `DynamicSandboxResponse` - ответ для dynamic-analysis этапа.
