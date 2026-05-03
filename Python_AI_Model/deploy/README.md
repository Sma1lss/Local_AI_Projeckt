# deploy

Каталог содержит артефакты разворачивания Python AI-контура.

## Структура

- `docker/` - Dockerfile для Python-сервисов.
- `systemd/` - unit-файлы для Linux/systemd-сценария.

## Как использовать

- Локально на Windows удобнее запускать сервисы через `scripts/run_services.ps1`.
- В контейнерном сценарии используется `docker-compose.yml` из родительской папки.
- В Linux-сценарии можно брать unit-файлы отсюда как основу для production deployment.
