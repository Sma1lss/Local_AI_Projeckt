# scanner/impl

Конкретные реализации сканеров.

## Streaming scanners

- `TikaMimeScanner` - определяет MIME по первым байтам.
- `EntropySignatureScanner` - проверяет сигнатуры и высокую энтропию.
- `ClamAvScanner` - стримит файл в ClamAV.
- `MacroScriptScanner` - ищет макросы и script-indicators.

## Post-scan scanners

- `YaraPostScanner` - запускает `yara` CLI по уже принятому файлу.
- `DynamicSandboxScanner` - отправляет файл во внешний dynamic sandbox.

## Состояние

- `YaraPostScanner` и `DynamicSandboxScanner` по умолчанию выключены конфигом.
