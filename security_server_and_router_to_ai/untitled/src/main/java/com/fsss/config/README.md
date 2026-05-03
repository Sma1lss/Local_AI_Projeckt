# config

Конфигурационный пакет сервиса `fsss`.

## Классы

- `AppConfig` - создает `Scheduler`, `WebClient`, `DataBufferFactory`.
- `FsssProperties` - типизированная модель конфигурации `fsss.*` со всеми вложенными секциями.

## За что отвечает пакет

- лимиты upload;
- режим spool (`MEMORY`, `TMPFS`, `HYBRID`);
- API key и security-лимиты;
- downstream URL;
- ClamAV;
- scan-правила;
- sandbox mode;
- security logging webhook.
