# security_server_and_router_to_ai/untitled

Основной Java-сервис безопасной загрузки файлов. В коде он именуется `fsss` и может работать в двух профилях:

- `edge` - внешний API-шлюз на `8080`, который принимает upload, спулит файл, зовет sandbox и затем форвардит clean-файл downstream-сервису;
- `sandbox` - внутренний scanner-service на `8081`, который выполняет streaming/post-scan проверки.

## Структура

- `src/main/java/com/fsss/` - production-код.
- `src/main/resources/` - `application.yml` и логирование.
- `src/test/java/com/fsss/` - тесты по контроллерам, сканерам и multipart-parser.
- `Dockerfile` - контейнерный образ.
- `docker-compose.yml` - готовый стенд `edge + sandbox + clamav`.

## Запуск

### Через Docker Compose

```powershell
Set-Location B:\Local_AI_Projeckt\security_server_and_router_to_ai\untitled
docker compose up --build
```

### Локально через Gradle

```powershell
Set-Location B:\Local_AI_Projeckt\security_server_and_router_to_ai\untitled
$env:JAVA_HOME='B:\java\jdk-17.0.12'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
cmd /c gradlew.bat bootRun --args="--spring.profiles.active=edge"
```

Для sandbox-профиля заменить `edge` на `sandbox`.

## Endpoint'ы

### Профиль `edge`

- `POST /api/scan`
- `GET /actuator/health`

### Профиль `sandbox`

- `POST /internal/scan`
- `POST /internal/dynamic`
- `GET /actuator/health`

## Интеграции

- API key authentication через `X-API-Key` или `Authorization: Bearer ...`.
- ClamAV как streaming-антивирус.
- Tika MIME detection.
- эвристический поиск сигнатур и макросов.
- опциональные YARA и dynamic-sandbox post-scan этапы.

## Ограничения

- `dynamic` сейчас возвращает заглушку `dynamic analysis not configured` и по умолчанию выключен.
- `yara` по умолчанию выключен и требует установленный `yara` CLI с rules file.
- downstream URL по умолчанию `http://127.0.0.1:8090/files`, а примерный `webServer` в этом репозитории реализует `POST /api/upload`, поэтому полная связка из коробки не замкнута.
