# com/fsss tests

Корневой package тестов сервиса `fsss`.

## Что проверяется

- edge endpoint возвращает verdict при mock sandbox/downstream;
- ClamAV scanner корректно обрабатывает ответ сервера;
- `BytePatternScanner` ловит сигнатуру на границе чанков;
- `MultipartStreamingParser` корректно разбирает multipart body.
