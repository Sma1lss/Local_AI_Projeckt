# Local Inference Pipeline (MVP Scaffold)

This repository contains a local inference pipeline scaffold for:

- multi-model inference (CPU GGUF + optional GPU transformers)
- retrieval (RAG)
- code execution sandbox
- routing and aggregation controller

Main endpoint target: `POST /ask` on controller service.

## Directory layout

- `models/` model files (`.gguf`, tokenizer files, etc.)
- `services/` FastAPI microservices
- `data/` documents and indexes
- `logs/` runtime logs
- `scripts/` bootstrap, download, indexing utilities
- `tests/` unit/integration tests

## Quick start (Windows PowerShell in this workspace)

1. Create/prepare environment:
   - `powershell -ExecutionPolicy Bypass -File scripts/bootstrap.ps1`
2. Download assets and models (resumable):
   - `B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe scripts\download_assets.py --manifest config\models.json`
3. Index local docs:
   - `B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe services\index_faiss.py --docs-dir data\docs --index-dir data\index`
4. Start services (separate terminals):
   - `B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe -m uvicorn services.retriever:app --host 0.0.0.0 --port 8002`
   - `B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe -m uvicorn services.router:app --host 0.0.0.0 --port 8003`
   - `B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe -m uvicorn services.executor:app --host 0.0.0.0 --port 8004`
   - `B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe -m uvicorn services.controller:app --host 0.0.0.0 --port 8000`

## Notes

- In this environment, modern compiled Python wheels may be unavailable.
- Services include runtime fallbacks:
  - no `faiss` -> pure Python vector index
  - no `sentence-transformers` -> deterministic hash embedding
  - no `docker` -> local subprocess sandbox with timeout
- For production, run on Linux/WSL2 and install `requirements.txt`.

## Windows binary note

On Windows, local CMake build may be unavailable. Use:
- `B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe scripts\fetch_llamacpp_windows_release.py`
This downloads the latest release zip and stages binaries to `llama.cpp\build\bin`.

## Judge/synthesizer

Final selection now uses a small judge model that evaluates candidates and synthesizes the best answer.
Set `JUDGE_URL` to a lightweight model endpoint (default in .env.example uses llama3).




