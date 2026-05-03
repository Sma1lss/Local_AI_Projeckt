# Retriever Service Guide

Сервис: `Python_AI_Model/services/retriever.py`
Порт по умолчанию: `8002`
Endpoint'ы: `GET /health`, `POST /index`, `POST /search`

## Роль сервиса

`retriever` отвечает за локальный retrieval-слой: читает документы, режет их на chunks, строит embeddings, хранит индекс и отдает top-k passages по query.

## Подробный разбор функций

- `_load_st_model()` - лениво поднимает `SentenceTransformer`.
- `embed_texts(texts)` - строит embeddings через model или deterministic fallback.
- `load_documents(docs_dir)` - обходит каталог, читает текст и режет документы на chunks.
- `save_index(docs, embs)` - пишет индекс в JSON.
- `load_index()` - читает JSON и optional FAISS index.
- `health()` - показывает состояние загруженного индекса.
- `index(req)` - перестраивает индекс.
- `search(req)` - ищет passages по FAISS или cosine fallback.

## Что уже готово

- полный pipeline `docs -> embeddings -> index -> search`;
- fallback без `faiss`;
- fallback без `sentence-transformers`;
- health endpoint.

## Что в процессе разработки

- incremental reindex;
- richer metadata filtering;
- рабочее наполнение базы знаний;
- стратегия обновления индекса без полной пересборки.

## Что важно разработчику и AI-агенту

- Изменение chunking rules ломает совместимость со старым индексом.
- Изменение структуры `passages` потребует проверки `controller`.
