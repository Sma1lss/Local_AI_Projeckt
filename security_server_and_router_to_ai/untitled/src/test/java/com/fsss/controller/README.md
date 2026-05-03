# controller tests

Тесты HTTP-контроллеров edge-профиля.

## Файл

- `UploadControllerTest.java` - поднимает контекст `edge`, мокает `SandboxClient` и `DownstreamClient`, отправляет multipart body и проверяет `CLEAN` verdict.
