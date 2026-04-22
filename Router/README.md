# Router Service (C++)

Local microservice that queries all configured models in parallel, then sends all answers to a separate judge model to produce the final response. History is stored on disk per chat.

## Build

```powershell
cd B:\Local_AI_Projeckt\Router
cmake -S . -B build
cmake --build build --config Release
```

## Run

The service reads model endpoints from:
1) `EXPERT_ENDPOINTS` environment variable (JSON object)
2) `B:\Local_AI_Projeckt\Router\config\endpoints.json`
3) `B:\Local_AI_Projeckt\Python_AI_Model\.env`
4) `B:\Local_AI_Projeckt\Python_AI_Model\.env.example`

Example env (PowerShell):

```powershell
$env:EXPERT_ENDPOINTS='{"codellama":"http://127.0.0.1:8001","starcoder2":"http://127.0.0.1:8011","llama3":"http://127.0.0.1:8010","mpt3":"http://127.0.0.1:8012"}'
$env:JUDGE_MODEL='llama3'
$env:ROUTER_PORT='9005'
B:\Local_AI_Projeckt\Router\build\Release\router_service.exe
```

## API

### POST /ask

Request JSON (flexible field names):

```json
{
  "query": "Your question",
  "chat_id": "optional",
  "new_chat": false
}
```

Accepted aliases: `question` or `message` instead of `query`, `chatId` instead of `chat_id`, `newChat` instead of `new_chat`.

Response JSON:

```json
{
  "chat_id": "chat_...",
  "new_chat": true,
  "answer": "final judge answer",
  "judge": { "model": "llama3", "ok": true, "error": "", "latency_ms": 1234 },
  "used_models": ["codellama", "starcoder2"],
  "candidates": [
    {"model": "codellama", "ok": true, "error": "", "latency_ms": 1200, "text": "..."}
  ],
  "literature": [
    {"doc_id": "file.md::0", "path": "...", "score": 0.42, "snippet": "..."}
  ]
}
```

### GET /health
Returns current configuration and status.

## History storage

History is stored under `B:\Local_AI_Projeckt\Router\history`:

- `Router\history\<chat_id>\models\<model>.jsonl`
- `Router\history\<chat_id>\judge.jsonl`
- `Router\history\<chat_id>\meta.json`

Each history file is capped to the last 10 turns by default (configurable).

## Config

Environment variables (all optional):

- `BASE_DIR` (default: project root)
- `EXPERT_ENDPOINTS` (JSON map)
- `JUDGE_MODEL` (default: `llama3`)
- `JUDGE_URL` (optional override)
- `MODEL_TIMEOUT_MS` (default: 20000)
- `JUDGE_TIMEOUT_MS` (default: 25000)
- `MODEL_MAX_TOKENS` (default: 512)
- `JUDGE_MAX_TOKENS` (default: 512)
- `MODEL_TEMPERATURE` (default: 0.2)
- `HISTORY_MAX_TURNS` (default: 10)
- `HISTORY_CONTEXT_TURNS` (default: 10)
- `LITERATURE_DIR` (default: `B:\Local_AI_Projeckt\literature`)
- `LITERATURE_TOP_K` (default: 4)
- `LITERATURE_MIN_SCORE` (default: 0.18)
- `LITERATURE_CHUNK_CHARS` (default: 1200)
- `LITERATURE_SNIPPET_CHARS` (default: 700)
- `ROUTER_HOST` (default: 0.0.0.0)
- `ROUTER_PORT` (default: 9005)

Notes:
- The judge model is excluded from the worker list to ensure separation.
- Literature is used only if similarity crosses `LITERATURE_MIN_SCORE`.


