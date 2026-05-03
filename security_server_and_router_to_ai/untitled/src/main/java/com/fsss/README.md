# com/fsss

Корневой package сервиса `fsss`.

## Пакеты

- `config/` - бин-конфигурация и strongly-typed properties.
- `controller/` - HTTP endpoints.
- `domain/` - базовые record/enum-модели домена.
- `dto/` - API-ответы и mapper.
- `exception/` - типизированные runtime-ошибки.
- `logging/` - security logging и webhook.
- `metrics/` - Micrometer counters/timers.
- `sandbox/` - контракт вызова sandbox из edge-сервиса.
- `scanner/` - pipeline и контракты сканеров.
- `security/` - API key auth, rate limiting, in-flight limiting.
- `service/` - orchestration-слой.
- `util/` - multipart parser и вспомогательные утилиты.

## Точка входа

- `FsssApplication.java` - `@SpringBootApplication` + `@ConfigurationPropertiesScan`.
