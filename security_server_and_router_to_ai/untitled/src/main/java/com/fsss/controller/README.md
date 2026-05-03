# controller

HTTP-контроллеры сервиса `fsss`.

## Классы

- `UploadController` - внешний endpoint `POST /api/scan` для профиля `edge`.
- `SandboxController` - внутренние endpoints `POST /internal/scan` и `POST /internal/dynamic` для профиля `sandbox`.
- `GlobalExceptionHandler` - переводит исключения в HTTP-ответы `ErrorResponse`.
