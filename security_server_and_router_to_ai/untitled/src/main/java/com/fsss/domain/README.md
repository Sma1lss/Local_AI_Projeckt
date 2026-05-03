# domain

Базовые доменные модели сервиса.

## Содержимое

- `FileMetadata` - имя файла, MIME, размер, hash, IP, User-Agent.
- `ScanFinding` - результат одного сканера.
- `ScanReport` - полный отчет о сканировании файла.
- `ScanVerdict` - итоговый вердикт: `CLEAN`, `SUSPICIOUS`, `MALICIOUS`, `ERROR`.
