# tests

Тесты Python AI-контура.

## Что покрыто

- `test_router.py` - rule-based классификация в `router`.
- `test_controller_integration.py` - интеграционный happy-path `controller` с mock-подменой `_post`.
- `test_executor.py` - запуск простого Python-кода через `executor`.
- `test_regression_bugfix.py` - регрессионная проверка Python execution path.

## Как запускать

```powershell
Set-Location B:\Local_AI_Projeckt\Python_AI_Model
B:\Local_AI_Projeckt\Python_AI_Model\.venv\bin\python.exe -m pytest tests -q
```

## Текущее состояние проверок

- `test_router.py` и `test_controller_integration.py` проходят в текущем окружении.
- тесты `executor` в текущей sandbox-среде зависают при запуске подпроцесса и требуют отдельной отладки окружения.
