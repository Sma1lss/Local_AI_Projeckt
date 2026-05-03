# services

Каталог с бизнес-логикой Python AI-контура.

## Модули

- `common.py` - общие dataclass-модели и функции скоринга.
- `controller.py` - основной orchestration endpoint `/ask`.
- `executor.py` - API для запуска кода.
- `index_faiss.py` - CLI для построения индекса.
- `model_server_hf.py` - inference endpoint для HF-модели.
- `model_server_llamacpp.py` - inference endpoint для GGUF-моделей через `llama.cpp`.
- `retriever.py` - индексация и поиск контекста.
- `router.py` - rule-based/LLM-based классификация запроса.

## Как запускать модули

Примеры:

```powershell
Set-Location B:\Local_AI_Projeckt\Python_AI_Model
B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe -m uvicorn services.controller:app --host 0.0.0.0 --port 8000
B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe -m uvicorn services.retriever:app --host 0.0.0.0 --port 8002
```

Или весь комплект сразу через `scripts/run_services.ps1`.

## Что здесь уже готово

- минимальный multi-model orchestration;
- RAG с fallback без `faiss`;
- judge-сборка финального ответа;
- модельные endpoint'ы для `llama.cpp` и `transformers`;
- code execution API.

## Что еще не доведено до production

- устойчивый sandbox для executor;
- стабильная проверка executor-тестов в текущем окружении;
- полноценная сессия диалога и persistent state на уровне Python-controller.
