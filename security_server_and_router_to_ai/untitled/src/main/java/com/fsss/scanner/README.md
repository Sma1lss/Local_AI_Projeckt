# scanner

Пакет с общими контрактами и конвейером сканирования.

## Содержимое

- `StreamingScanner` - контракт потокового сканера.
- `PostScanScanner` - контракт сканера, работающего после полного приема файла.
- `ScanHandle` - handle одного streaming-scanner instance.
- `ScanContext` - scan ID, metadata и время старта.
- `ScannerOutcome` - `CLEAN`, `SUSPICIOUS`, `MALICIOUS`, `ERROR`, `SKIPPED`.
- `ScannerPipeline` - orchestration всех `StreamingScanner`.
- `impl/` - конкретные реализации сканеров.
