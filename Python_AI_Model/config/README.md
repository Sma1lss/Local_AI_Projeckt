# config

Каталог хранит конфигурацию скачивания и раскладки моделей.

## Содержимое

- `models.json` - манифест ассетов для `scripts/download_assets.py`.

## Что описывает `models.json`

- архив исходников `llama.cpp`;
- GGUF-файлы для `codellama`, `llama3`, `mpt3`;
- HF-модель `starcoder2`;
- embedding-модель `all-MiniLM-L6-v2`.

## Как используется

- `scripts/download_assets.py` читает манифест и скачивает файлы по `target_path`;
- `scripts/download_models.ps1` запускает этот процесс вместе с подготовкой `llama.cpp`;
- `services/model_server_*` потом читают реальные пути к моделям из `.env` и переменных окружения.

Эта папка сама по себе не запускается: запуск идет через `B:\Local_AI_Projeckt\Python_AI_Model`.
