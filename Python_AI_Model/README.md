# Python_AI_Model

Папка содержит Python-реализацию локального AI-контура: поиск контекста, маршрутизацию, исполнение кода, оркестрацию нескольких моделей и judge/synthesizer.

## Что лежит внутри

- `config/` - манифест моделей и ассетов.
- `data/` - документы для RAG, индекс и временные каталоги для executor.
- `deploy/` - Dockerfile и systemd unit-файлы.
- `models/` - локально скачанные модели и embedding-ассеты.
- `scripts/` - bootstrap, скачивание, запуск сервисов, патчи окружения.
- `services/` - FastAPI-микросервисы и CLI для индексации.
- `tests/` - unit/integration тесты.
- `llama.cpp/` - локальная копия upstream-проекта и/или staged binaries.

## Какие сервисы поднимаются из этой папки

| Сервис | Файл | Порт | Назначение |
| --- | --- | --- | --- |
| `controller` | `services/controller.py` | `8000` | Главный `/ask`, orchestration и judge |
| `retriever` | `services/retriever.py` | `8002` | Индексация и поиск контекста |
| `router` | `services/router.py` | `8003` | Маршрутизация запроса по тегам |
| `executor` | `services/executor.py` | `8004` | Выполнение Python/C/C++ кода |
| `model_server_llamacpp` | `services/model_server_llamacpp.py` | `8001/8010/8012` | GGUF-модели через `llama.cpp` |
| `model_server_hf` | `services/model_server_hf.py` | `8011` | HF/Transformers-модель |

## Запуск

### Вариант 1. Полный локальный запуск через PowerShell-скрипты

```powershell
Set-Location B:\Local_AI_Projeckt\Python_AI_Model
powershell -ExecutionPolicy Bypass -File scripts\bootstrap.ps1
powershell -ExecutionPolicy Bypass -File scripts\download_models.ps1
B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe services\index_faiss.py --docs-dir data\docs --index-dir data\index
powershell -ExecutionPolicy Bypass -File scripts\run_services.ps1
```

### Вариант 2. Запуск по сервисам вручную

```powershell
Set-Location B:\Local_AI_Projeckt\Python_AI_Model
B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe -m uvicorn services.retriever:app --host 0.0.0.0 --port 8002
B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe -m uvicorn services.router:app --host 0.0.0.0 --port 8003
B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe -m uvicorn services.executor:app --host 0.0.0.0 --port 8004
B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe -m uvicorn services.controller:app --host 0.0.0.0 --port 8000
```

При необходимости model servers поднимаются отдельно тем же `uvicorn`, но чаще удобнее использовать `scripts/run_services.ps1`.

### Вариант 3. Docker Compose

```powershell
Set-Location B:\Local_AI_Projeckt\Python_AI_Model
docker compose up --build
```

## Обязательные подготовительные шаги

1. Создать виртуальное окружение и поставить зависимости через `bootstrap.ps1`.
2. Скачивать модели и `llama.cpp` лучше через `download_models.ps1`.
3. Если `llama.cpp` не собирается локально на Windows, использовать `scripts/fetch_llamacpp_windows_release.py`.
4. Перед RAG-запросами обязательно построить индекс по документам.

## Полезные файлы

- `.env.example` - базовый набор переменных среды.
- `config/models.json` - список скачиваемых моделей.
- `docker-compose.yml` - готовая локальная схема запуска.
- `requirements.txt` - полный набор Python-зависимостей.
- `requirements.windows-mingw.txt` - пакетный набор для текущего Windows/MSYS-окружения.

## Ограничения текущей реализации

- `executor` использует локальные подпроцессы и только опционально Docker.
- `retriever` умеет работать без `faiss` и `sentence-transformers`, но в fallback-режиме качество ниже.
- `data/docs` и корневая `literature/` сейчас не заполнены, поэтому RAG без наполнения базы знаний будет формальным.
