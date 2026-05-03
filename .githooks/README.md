# Git Hooks

В репозитории используется versioned hook из каталога `.githooks/`.

Основной Windows-entrypoint:

- `pre-commit.cmd`

Рабочая логика находится в:

- `pre-commit.ps1`

Поведение:

- автоматически снимает со staging все файлы больше `100 MB`;
- файл не удаляется с диска;
- изменения не попадают в текущий commit;
- для tracked-файла изменения остаются как unstaged;
- для нового файла он остается в рабочей директории как untracked.

Если локальная привязка hook path слетела, восстановить ее можно так:

```powershell
git config core.hooksPath .githooks
```