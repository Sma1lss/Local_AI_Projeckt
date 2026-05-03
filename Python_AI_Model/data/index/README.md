# data/index

Каталог сгенерированного retrieval-индекса.

## Файлы

- `docs.json` - сериализованные chunks документов.
- `embeddings.json` - embeddings этих chunks.
- `faiss.index` - бинарный FAISS-индекс, если `faiss` доступен.

## Как обновляется

Пересоздается командой `services/index_faiss.py` или endpoint'ом `/index` retriever-сервиса.
