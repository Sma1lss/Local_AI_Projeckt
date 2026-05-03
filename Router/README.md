# Router

Отдельный C++-сервис, который:

1. поднимает HTTP API;
2. выбирает все доступные worker-модели;
3. вызывает их параллельно;
4. собирает ответы;
5. отправляет кандидатов в judge-модель;
6. сохраняет историю по чатам на диск;
7. подтягивает локальные сниппеты из `B:\Local_AI_Projeckt\literature`.

## Структура

- `config/` - JSON-конфиг endpoint'ов.
- `history/` - история по чатам в JSONL.
- `src/` - весь исходный код сервиса.
- `third_party/` - vendored-зависимости `httplib.h` и `json.hpp`.

## Сборка

```powershell
Set-Location B:\Local_AI_Projeckt\Router
cmake -S . -B build
cmake --build build --config Release
```

## Запуск

Сервис читает endpoints в таком порядке:

1. `EXPERT_ENDPOINTS` из environment;
2. `Router\config\endpoints.json`;
3. `Python_AI_Model\.env`;
4. `Python_AI_Model\.env.example`;
5. корневые `.env`/`.env.example`, если они появятся.

Пример:

```powershell
Set-Location B:\Local_AI_Projeckt\Router
$env:EXPERT_ENDPOINTS='{"codellama":"http://127.0.0.1:8001","starcoder2":"http://127.0.0.1:8011","llama3":"http://127.0.0.1:8010","mpt3":"http://127.0.0.1:8012"}'
$env:JUDGE_MODEL='llama3'
$env:ROUTER_PORT='9005'
B:\Local_AI_Projeckt\Router\build\Release\router_service.exe
```

## API

### `GET /health`

Возвращает:

- базовую конфигурацию;
- пути к `base_dir`, `literature_dir`, `history_dir`;
- текущую judge-модель;
- список endpoints;
- признак, была ли проиндексирована `literature/`.

### `POST /ask`

Поддерживает поля:

- `query`, `question` или `message`;
- `chat_id` или `chatId`;
- `new_chat` или `newChat`.

Ответ содержит:

- `chat_id`;
- `answer` judge-модели;
- `judge` с latency и ошибкой;
- `used_models`;
- `candidates` от worker-моделей;
- `literature` со сниппетами локального индекса.

## История

История хранится в `Router\history\<chat_id>\`:

- `models\<model>.jsonl` - ответы каждой модели;
- `judge.jsonl` - финальные judge-ответы;
- `meta.json` - метаданные чата.

## Ограничения

- поддерживается только `http`, без `https`;
- тестов в папке пока нет;
- нет аутентификации и rate limiting;
- поиск по `literature/` работает только если папка реально заполнена.
