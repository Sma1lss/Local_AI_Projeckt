# exception

Типизированные исключения сервиса `fsss`.

## Классы

- `RequestRejectedException` - базовое исключение с `HttpStatus`.
- `FileTooLargeException` - `413 Payload Too Large`.
- `MultipartParsingException` - `400 Bad Request`.
- `UnsupportedMediaTypeException` - `415 Unsupported Media Type`.
- `ScanFailedException` - `500 Internal Server Error` для сбоев сканирования.
