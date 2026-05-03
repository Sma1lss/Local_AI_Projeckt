# config

Конфигурация C++ Router.

## Что ожидается здесь

- `endpoints.json` - словарь вида `{ "model_name": "http://host:port" }`.

## Приоритет загрузки

Даже если файл отсутствует, Router все равно может стартовать, если endpoints заданы в `EXPERT_ENDPOINTS` или в `.env` Python-контура.

## Пример структуры

```json
{
  "codellama": "http://127.0.0.1:8001",
  "starcoder2": "http://127.0.0.1:8011",
  "llama3": "http://127.0.0.1:8010",
  "mpt3": "http://127.0.0.1:8012"
}
```
