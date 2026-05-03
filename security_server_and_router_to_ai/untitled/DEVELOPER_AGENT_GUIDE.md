# FSSS Developer / Agent Guide

Проект: `security_server_and_router_to_ai/untitled`
Технологии: Java 17, Spring Boot, WebFlux, Spring Security, Micrometer

## Что это за сервис

Один кодовый проект запускается в двух профилях:

- `edge` - внешний API-шлюз загрузки файлов;
- `sandbox` - внутренний сервис потокового и post-scan анализа.

## Какие guide-файлы есть внутри

- `EDGE_SERVICE_GUIDE.md`
- `SANDBOX_SERVICE_GUIDE.md`

## Что уже готово

- API key authentication;
- rate limit и in-flight limit;
- streaming multipart parser;
- hybrid spool storage;
- Tika MIME detection;
- entropy/signature/macro scanners;
- ClamAV integration;
- optional YARA/dynamic hooks;
- downstream forwarding из edge.

## Что в процессе разработки

- полноценный dynamic analysis;
- рабочая склейка с downstream сервисом этого репозитория;
- production-grade retry/queue model;
- полная автоматическая test verification в текущем sandbox-окружении.

## Что важно разработчику и AI-агенту

- Это самый критичный по безопасности сервис во всем репозитории.
- Любые изменения в multipart parsing, spool handling и security filters должны рассматриваться как high-risk.
- Профили `edge` и `sandbox` нельзя документировать как одну и ту же роль: они выполняют разные функции.
