# Router Developer / Agent Guide

Сервис: `Router/src/main.cpp`
Технологии: C++17, `cpp-httplib`, `nlohmann::json`
Порт по умолчанию: `9005`

## Роль сервиса

Это самостоятельный C++-оркестратор, который:

1. принимает пользовательский вопрос;
2. подмешивает контекст из локальной `literature/`;
3. параллельно вызывает worker-модели;
4. отправляет их ответы в judge model;
5. хранит историю по чатам на диск.

## Ключевые части логики

- `Config` - агрегирует runtime-конфигурацию.
- `load_config()` - собирает env vars, `.env` и JSON endpoints.
- `LiteratureIndex` - локальный semantic search по `literature/`.
- `call_model(...)` - единый HTTP-вызов к model endpoint.
- JSONL helpers - чтение и запись истории.
- `main()` - регистрирует `/health` и `/ask`, а также реализует весь orchestration flow.

## Что уже готово

- parallel fan-out по моделям;
- judge synthesis;
- chat history в `Router/history/`;
- retrieval по `literature/`;
- простой health endpoint.

## Что в процессе разработки

- `https` support;
- auth/rate limiting;
- тесты;
- streaming responses;
- production-grade error handling.

## Что важно разработчику и AI-агенту

- Judge model исключается из списка worker-моделей специально.
- Любая правка формата history JSONL повлияет на обратную совместимость чатов.
- Качество retrieval сильно зависит от наполнения `literature/`, а папка сейчас пустая.
