# data

Рабочие данные Python AI-контура.

## Структура

- `docs/` - исходные документы для RAG.
- `index/` - сгенерированные индексы (`docs.json`, `embeddings.json`, `faiss.index`).
- `exec_tmp/` - временные каталоги и файлы, которые использует `executor`.

## Как заполняется

1. Документы кладутся в `docs/`.
2. Индекс строится командой:

```powershell
Set-Location B:\Local_AI_Projeckt\Python_AI_Model
B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe services\index_faiss.py --docs-dir data\docs --index-dir data\index
```

3. `exec_tmp/` заполняется автоматически во время работы `executor`.

## Замечания

- Если `docs/` пустая, `retriever` не сможет вернуть полезный контекст.
- `index/` можно безопасно пересоздавать при изменении базы знаний.
