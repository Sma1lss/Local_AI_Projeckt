# dto

API DTO для внешнего ответа edge-сервиса.

## Классы

- `UploadResponse` - JSON-ответ для клиента после `POST /api/scan`.
- `UploadResponseMapper` - MapStruct-контракт маппинга.
- `UploadResponseManualMapper` - primary-реализация без генерации кода.
- `ErrorResponse` - единая форма ошибки.
