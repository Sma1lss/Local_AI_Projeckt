# Router Service Guide

Сервис: `Python_AI_Model/services/router.py`
Порт по умолчанию: `8003`
Endpoint'ы: `GET /health`, `POST /route`

## Роль сервиса

`router` определяет тип пользовательского запроса и возвращает route tags, которые использует `controller`.

Текущие теги:

- `code`
- `math`
- `design`
- `general`
- `reasoning`

## Внутренняя логика

Сервис работает в три слоя:

1. rule-based regex routing;
2. optional LLM fallback;
3. default fallback `general`.

## Подробный разбор функций

- `_rules(query)` - regex-классификация по `CODE_RE`, `MATH_RE`, `DESIGN_RE`.
- `_llm_fallback(query)` - вызывает внешнюю route-LLM, если задан `ROUTER_LLM_URL`.
- `health()` - возвращает текущее значение `router_llm_url`.
- `route(req)` - основной endpoint, который комбинирует rules и fallback.

## Что уже готово

- очень быстрый локальный routing;
- fallback через отдельную модель;
- простой стабильный API.

## Что в процессе разработки

- более точный набор правил;
- richer taxonomy маршрутов;
- валидация качества классификации;
- нормальный JSON parsing из ответа LLM fallback.

## Что важно разработчику и AI-агенту

- Если добавить новый route tag, нужно синхронно менять `controller.select_experts()`.
- Если менять regex, надо прогнать `tests/test_router.py`.
