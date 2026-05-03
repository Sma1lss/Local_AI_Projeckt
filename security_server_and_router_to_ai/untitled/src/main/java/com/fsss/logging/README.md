# logging

Логирование событий безопасности.

## Класс

- `SecurityEventLogger` - пишет warn-события в security-лог и опционально отправляет webhook.

## Какие события логируются

- malware/suspicious findings;
- rate limit exceed;
- превышение in-flight лимита;
- suspicious filenames.
