# deploy/systemd

Systemd unit-файлы для Linux-развертывания Python AI-контура.

## Файлы

- `llm-controller.service`
- `llm-executor.service`
- `llm-model-codellama.service`
- `llm-retriever.service`
- `llm-router.service`

Каждый unit можно использовать как основу для production deployment на Linux-хосте после адаптации путей и переменных среды.
