# Sandbox Service Guide

Профиль: `sandbox`
Основной endpoint: `POST /internal/scan`
Порт по умолчанию: `8081`

## Роль микросервиса

`sandbox` - внутренний scanning service. Он принимает уже отфильтрованный upload из `edge`, прогоняет потоковые сканеры и post-scan этапы, после чего возвращает итоговый verdict и findings.

## Основной flow

1. `SandboxController.scan()` принимает multipart request.
2. `StreamingScanProcessor` использует `MultipartStreamingParser` и `ScannerPipeline`.
3. В процессе чтения файла работают streaming scanners:
   - `TikaMimeScanner`
   - `EntropySignatureScanner`
   - `ClamAvScanner`
   - `MacroScriptScanner`
4. После полного приема файла `PostScanService` вызывает post-scan scanners:
   - `YaraPostScanner`
   - `DynamicSandboxScanner`
5. `ScanFindingAnalyzer` объединяет findings и вычисляет итоговый verdict.
6. `ScanMetrics` пишет метрики.
7. spool безопасно удаляется.

## Критичные классы

- `SandboxController`
- `SandboxScanService`
- `StreamingScanProcessor`
- `ScannerPipeline`
- `PostScanService`
- `ScanFindingAnalyzer`
- `HybridSpooler`
- `MultipartStreamingParser`

## Что уже готово

- streaming scan pipeline;
- MIME detection;
- signature/entropy/macros heuristics;
- ClamAV integration;
- optional hooks под YARA и dynamic sandbox;
- metrics по verdict и duration.

## Что в процессе разработки

- реально включенный dynamic analysis;
- production YARA workflow;
- richer policies для verdict aggregation;
- более полное покрытие тестами.

## Что важно разработчику и AI-агенту

- Изменения тут напрямую влияют на итоговый security verdict.
- Multipart parser и spool handling являются высокорисковыми зонами.
- `dynamic` сейчас логически недостроен: endpoint существует, но по умолчанию это заглушка.
