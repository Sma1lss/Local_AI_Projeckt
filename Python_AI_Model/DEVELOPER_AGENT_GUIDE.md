# Python_AI_Model Developer / Agent Guide

Документ описывает весь Python-контур как набор отдельных FastAPI-микросервисов.

## Состав микросервисов

- `controller` - главный orchestration endpoint `POST /ask`.
- `retriever` - построение и поиск retrieval-контекста.
- `router` - классификация запроса по типу задачи.
- `executor` - запуск Python/C/C++ кода для проверки кандидатов.
- `model_server_llamacpp` - inference для GGUF-моделей.
- `model_server_hf` - inference для HF/Transformers-модели.

## Что уже готово

- раздельные FastAPI-сервисы с собственными endpoint'ами;
- старт через `uvicorn`, `docker-compose.yml` и `scripts/run_services.ps1`;
- базовый RAG;
- вызов нескольких моделей и judge synthesis;
- fallback-режимы без `faiss`, `sentence-transformers` и full HF-stack.

## Что в разработке

- production-grade sandbox для `executor`;
- стабильная проверка execution path в текущем окружении;
- нормальный session/state layer;
- единая стратегия между этим Python-контуром и отдельным C++ `Router`.

## Карта guide-файлов по сервисам

- `services/CONTROLLER_SERVICE_GUIDE.md`
- `services/RETRIEVER_SERVICE_GUIDE.md`
- `services/ROUTER_SERVICE_GUIDE.md`
- `services/EXECUTOR_SERVICE_GUIDE.md`
- `services/MODEL_SERVER_HF_GUIDE.md`
- `services/MODEL_SERVER_LLAMACPP_GUIDE.md`

## Запуск всего контура

```powershell
Set-Location B:\Local_AI_Projeckt\Python_AI_Model
powershell -ExecutionPolicy Bypass -File scripts\bootstrap.ps1
powershell -ExecutionPolicy Bypass -File scripts\download_models.ps1
B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe services\index_faiss.py --docs-dir data\docs --index-dir data\index
powershell -ExecutionPolicy Bypass -File scripts\run_services.ps1
```
