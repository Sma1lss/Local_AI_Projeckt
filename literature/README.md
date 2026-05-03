# literature

Папка предназначена для материалов, которые используются двумя сервисами:

- `Python_AI_Model/services/retriever.py` индексирует документы для RAG;
- `Router/src/main.cpp` строит локальный in-memory индекс и подмешивает сниппеты в `judge`-prompt.

## Что сюда класть

Поддерживаемые расширения по коду:

- `.txt`
- `.md`
- `.rst`
- `.py`
- `.java`
- `.cpp`
- `.c`
- `.h`
- `.json`

## Текущее состояние

Сейчас каталог пустой. Это значит:

- `retriever` не сможет построить полезный индекс без загрузки документов;
- C++ Router вернет пустой массив `literature`, даже если сам сервис запущен корректно.

## Как использовать

1. Положить сюда документы по предметной области проекта.
2. Для Python-контура переиндексировать материалы:

```powershell
Set-Location B:\Local_AI_Projeckt\Python_AI_Model
B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe services\index_faiss.py --docs-dir data\docs --index-dir data\index
```

3. Для C++ Router просто перезапустить сервис: он индексирует `literature/` при старте.
