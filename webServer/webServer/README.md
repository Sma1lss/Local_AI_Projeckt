# webServer/webServer

Минимальный Spring Boot backend для загрузки файла.

## Что делает сервис

- принимает `multipart/form-data`;
- ожидает файл в поле `file`;
- создает папку `uploads/`, если ее нет;
- сохраняет файл на локальный диск;
- возвращает строковый ответ.

## Структура

- `build.gradle.kts` - зависимости Spring Boot.
- `src/main/java/org/example/Main.java` - точка входа.
- `src/main/java/org/example/FileUploaderApplication.java` - REST-контроллер.
- `src/main/java/org/example/resources/application/application.properties` - лимиты multipart.
- `src/main/java/org/example/resources/static/index.html` - пустая статическая страница.

## Запуск

```powershell
Set-Location B:\Local_AI_Projeckt\webServer\webServer
cmd /c gradlew.bat bootRun
```

Требуется JDK 17 и доступная Gradle wrapper distribution.

## API

- `POST /api/upload`

Формат:

- `multipart/form-data`
- поле: `file`

## Ограничения

- ответ возвращается как plain text, не JSON;
- бизнес-валидации почти нет;
- контракт не совпадает с FSSS downstream URL `POST /files`;
- в проекте нет тестов.
