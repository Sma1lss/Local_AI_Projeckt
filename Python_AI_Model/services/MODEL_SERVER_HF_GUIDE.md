# HF Model Server Guide

Сервис: `Python_AI_Model/services/model_server_hf.py`
Типичный порт: `8011`
Endpoint'ы: `GET /health`, `POST /infer`, `POST /embed`

## Роль сервиса

Этот микросервис изолирует доступ к Hugging Face/Transformers модели. Сейчас он в основном используется как endpoint для `starcoder2`.

## Подробный разбор функций

- `_load_pipeline()` - лениво загружает tokenizer и causal LM.
- `health()` - показывает имя модели, `MODEL_ID` и доступность HF stack.
- `infer(req)` - выполняет генерацию или возвращает fallback response.
- `embed(req)` - отдает deterministic embedding.

## Что уже готово

- отдельный inference endpoint;
- lazy-loading модели;
- fallback-режим без полного ML-stack;
- health endpoint.

## Что в процессе разработки

- streaming generation;
- batching;
- реальный `logprob` в ответе;
- production-ready memory management.

## Что важно разработчику и AI-агенту

- Fallback сейчас нужен для выживания в урезанном окружении, но он не отражает реальное поведение модели.
- Формат ответа `/infer` должен оставаться совместимым с `controller` и C++ `Router`.
