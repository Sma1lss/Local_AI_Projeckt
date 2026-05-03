# webServer Developer / Agent Guide

Сервис: `webServer/webServer`
Технологии: Java 17, Spring Boot MVC
Основной endpoint: `POST /api/upload`

## Роль сервиса

Это минимальный sample-сервис для загрузки файла. Он не является полноценным production downstream для `fsss-edge`, а служит скорее демонстрацией самого простого upload-flow.

## Основной flow

1. `Main.main()` поднимает Spring Boot приложение.
2. `FileUploaderApplication.handleFileUpload(file)`
   - проверяет, что файл не пустой;
   - создает каталог `uploads`, если он отсутствует;
   - сохраняет файл с исходным именем;
   - возвращает строковый HTTP-ответ.

## Что уже готово

- базовый multipart upload endpoint;
- запись файла на локальный диск;
- лимиты multipart через `application.properties`.

## Что в процессе разработки

- совместимость с контрактом `fsss-edge` (`POST /files`);
- JSON API вместо plain text;
- валидация имени файла и безопасности пути;
- тесты.

## Что важно разработчику и AI-агенту

- Сервис сейчас нельзя считать безопасным file-storage backend без дополнительной доработки.
- Он не выровнен по контракту с Java `edge`-сервисом безопасности.
