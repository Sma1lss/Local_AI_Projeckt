# service

Главный orchestration-слой сервиса `fsss`.

## Классы

- `FileScanService` - внешний edge-flow: принять, проверить, отправить downstream.
- `EdgeStreamProcessor` - принимает multipart stream в профиле `edge`.
- `SandboxScanService` - orchestration sandbox-flow.
- `StreamingScanProcessor` - принимает multipart stream в профиле `sandbox`.
- `PostScanService` - запускает post-scan scanners.
- `DownstreamClient` - отправляет clean-файл downstream-сервису.
- `ScanFindingAnalyzer` - собирает итоговый verdict и MIME.
- `HybridSpooler` / `Spooler` / `SpoolHandle` - спулинг в память/файл.
- `MultipartRequestFactory` - собирает multipart body из spool.
- `EdgeProcessingResult` / `ScanProcessingResult` - промежуточные record-результаты.
