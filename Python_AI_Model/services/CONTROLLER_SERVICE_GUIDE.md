# Controller Service Guide

Сервис: `Python_AI_Model/services/controller.py`
Порт по умолчанию: `8000`
Главный endpoint: `POST /ask`

## Роль сервиса

`controller` - центральный orchestration-layer Python AI-контура. Он не делает inference сам, а координирует остальные сервисы: принимает запрос, вызывает `router`, вызывает `retriever`, выбирает expert models, при необходимости использует `executor`, затем пытается собрать финальный ответ через judge model.

## Входной контракт

`AskRequest`:

- `query` - обязательный текст запроса;
- `mode` - строковый режим;
- `top_k` - сколько passages запрашивать у `retriever`.

## Выходной контракт

`AskResponse`:

- `answer` - финальный ответ;
- `confidence` - confidence после judge/fallback scoring;
- `provenance` - evidence snippets;
- `tests` - execution diagnostics;
- `used_models` - модели, участвовавшие в ответе;
- `candidates` - сводка по кандидатам.

## Подробный flow по функциям

- `_post(url, payload)` - базовый HTTP helper.
- `route_query(query)` - вызывает `router` и при ошибке возвращает `general`.
- `retrieve(query, top_k)` - вызывает `retriever/search`.
- `select_experts(route)` - переводит route tags в реальные model endpoints.
- `build_prompt(role, query, passages)` - собирает prompt для экспертной модели.
- `infer_expert(name, url, prompt)` - вызывает `/infer` у model service.
- `run_exec(answer)` - ищет Python code block и отдает его в `executor`.
- `score(query, passages, raws)` - считает overlap, sem-sim и execution result.
- `polish(text, passages)` - optional post-processing через synthesizer.
- `_extract_json(text)` - пытается достать JSON из judge output.
- `judge_synthesize(query, passages, scored, tests)` - строит финальный judge payload.
- `health()` - показывает URLs зависимостей.
- `ask(req)` - главный orchestration flow.

## Внешние зависимости

- `router` на `ROUTER_URL`;
- `retriever` на `RETRIEVER_URL`;
- `executor` на `EXECUTOR_URL`;
- model endpoints из `EXPERT_ENDPOINTS`;
- judge model из `JUDGE_URL`.

## Что уже готово

- parallel fan-out по моделям;
- routing + retrieval + scoring;
- execution-aware candidate evaluation;
- judge synthesis fallback chain;
- structured response.

## Что в процессе разработки

- нормальный session memory/state;
- более строгий parsing judge-response;
- отказоустойчивость при частичном падении model pool;
- выравнивание с отдельным C++ Router.

## Что важно разработчику и AI-агенту

- Любая правка формулы scoring влияет на ранжирование всех ответов.
- `run_exec()` меняет security posture всего контура.
- Если менять `select_experts()`, нужно синхронизировать это с реально поднятыми endpoints.
