# scripts

Служебные скрипты для подготовки и запуска Python AI-контура.

## Файлы

- `bootstrap.ps1` - создает структуру, готовит `.venv`, ставит зависимости и патчит `tempfile`.
- `download_assets.py` - скачивает модели и ассеты по манифесту с поддержкой retry/resume.
- `download_models.ps1` - orchestration-обертка вокруг `download_assets.py` и `fetch_llamacpp_windows_release.py`.
- `fetch_llamacpp_windows_release.py` - скачивает готовый Windows release `llama.cpp` и раскладывает бинарники в `llama.cpp/build/bin`.
- `run_services.ps1` - поднимает core-сервисы и model servers.
- `py_patches/sitecustomize.py` - runtime patch для проблемных временных каталогов в этом окружении.

## Основной сценарий

```powershell
Set-Location B:\Local_AI_Projeckt\Python_AI_Model
powershell -ExecutionPolicy Bypass -File scripts\bootstrap.ps1
powershell -ExecutionPolicy Bypass -File scripts\download_models.ps1
powershell -ExecutionPolicy Bypass -File scripts\run_services.ps1
```

## Важное

- Скрипты рассчитаны на текущее Windows/MSYS-окружение проекта.
- В production стоит перенести этот процесс в предсказуемый Linux/WSL2 deployment.
