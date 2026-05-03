# llama.cpp Model Server Guide

Сервис: `Python_AI_Model/services/model_server_llamacpp.py`
Типичные порты: `8001`, `8010`, `8012`
Endpoint'ы: `GET /health`, `GET /debug/cmd`, `POST /infer`, `POST /embed`

## Роль сервиса

Сервис является thin-wrapper'ом над CLI-бинарником `llama.cpp`. Он нужен, чтобы локальные GGUF-модели выглядели как обычные HTTP inference endpoints.

## Подробный разбор функций

- `health()` - возвращает имя модели, путь к модели и бинарнику.
- `_build_cmd(req)` - формирует команду `llama-cli`.
- `_extract_text(stdout, prompt)` - убирает prompt из stdout.
- `infer(req)` - валидирует наличие модели и бинарника, затем запускает `llama.cpp`.
- `embed(req)` - возвращает deterministic embedding.
- `debug_cmd()` - строит sample command для диагностики.

## Что уже готово

- единый HTTP-контракт поверх `llama.cpp`;
- поддержка разных GGUF-моделей через env vars;
- health и debug endpoint'ы;
- запуск на Windows с `.exe` fallback.

## Что в процессе разработки

- streaming token output;
- реальный `logprob`;
- более точная обработка stdout/stderr;
- уменьшение overhead на каждый запрос.

## Что важно разработчику и AI-агенту

- Сервис чувствителен к пути модели и совместимости бинарника `llama.cpp`.
- Если менять аргументы `_build_cmd()`, нужно проверять поддержку конкретной версии `llama.cpp`.
