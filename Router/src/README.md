# src

Весь исходный код C++ Router сейчас сосредоточен в одном файле `main.cpp`.

## Что реализовано в `main.cpp`

- чтение `.env` и JSON-конфигов;
- парсинг URL для model endpoints;
- параллельный вызов worker-моделей;
- judge-агрегация;
- локальный индекс по `literature/`;
- хранение истории в JSONL;
- HTTP API `/health` и `/ask`.

## Сборка

```powershell
Set-Location B:\Local_AI_Projeckt\Router
cmake -S . -B build
cmake --build build --config Release
```
