# models

Каталог для локально скачанных моделей и вспомогательных ML-ассетов.

## Что ожидается здесь

- GGUF-модели для `codellama`, `llama3`, `mpt3`.
- HF-ассеты для `starcoder2` и embedding-модели.

## Как заполняется

Обычно через:

```powershell
Set-Location B:\Local_AI_Projeckt\Python_AI_Model
powershell -ExecutionPolicy Bypass -File scripts\download_models.ps1
```

## Как используется

- `model_server_llamacpp.py` читает GGUF-файлы отсюда.
- `model_server_hf.py` использует эти данные как локальный cache/источник модели.
