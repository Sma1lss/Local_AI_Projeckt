# util

Низкоуровневые утилиты сервиса `fsss`.

## Классы

- `MultipartStreamingParser` - собственный streaming-parser multipart/form-data.
- `MultipartHeaders` - parsed headers одной part.
- `MultipartStreamConsumer` - callback-контракт для parser.
- `BytePatternScanner` - поиск byte-pattern с поддержкой пересечения чанков.
- `EntropyCalculator` - подсчет Shannon entropy.
- `FilenameSanitizer` - очистка имени файла и выявление suspicious path patterns.
- `Hex` - преобразование байтов в hex.
- `DetailsMap` - builder для `Map<String, Object>`.
- `SecureByteArrayOutputStream` - buffer с возможностью wipe.
