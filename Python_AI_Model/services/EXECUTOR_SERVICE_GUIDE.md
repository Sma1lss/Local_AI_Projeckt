# Executor Service Guide

Сервис: `Python_AI_Model/services/executor.py`
Порт по умолчанию: `8004`
Endpoint'ы: `GET /health`, `POST /execute`, `POST /execute_json`

## Роль сервиса

`executor` выполняет пользовательский или сгенерированный код. В текущей архитектуре он нужен `controller` для проверки кандидатов с Python-кодом, но API допускает и прямой вызов.

## Поддерживаемые языки

- `python`
- `cpp`
- `c`

## Подробный разбор функций

- `_docker_available()` - проверяет доступность Docker.
- `_run(cmd, cwd, timeout, stdin='')` - единая обертка над `subprocess.run`.
- `_run_python_local(workdir, req)` - пишет `main.py` и запускает Python локально.
- `_run_cpp_local(workdir, req)` - пишет `main.cpp`, компилирует через `g++`, затем запускает бинарник.
- `_run_python_docker(workdir, req)` - запускает Python-код в контейнере `python:3.11`.
- `health()` - показывает timeout, Docker mode и temp root.
- `execute(req)` - главный execution endpoint.
- `execute_json(req)` - debug/plain response variant.

## Что уже готово

- локальный execution API;
- cleanup temp dirs;
- optional Docker path;
- единый контракт ответа с stdout/stderr/runtime.

## Что в процессе разработки

- реальный secure sandbox;
- надежная стабильность execution в текущем окружении;
- более строгая модель тестовых раннеров;
- лучшая resource isolation.

## Известные ограничения

- В текущем окружении тесты execution path зависают.
- Локальный запуск Python и `g++` не является полноценной изоляцией.

## Что важно разработчику и AI-агенту

- Любая правка тут влияет на security posture всего AI-контура.
- Изменения в `ExecuteResponse` нужно синхронизировать с `controller.run_exec()`.
