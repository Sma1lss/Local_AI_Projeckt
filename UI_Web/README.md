# UI_Web

Статический фронтенд-каркас проекта.

## Структура

- `index.html`
- `chat.html`
- `profile.html`
- `css/style.css`
- `js/api.js`
- `js/chat.js`
- `js/disciplines.js`
- `js/profile.js`

## Текущее состояние

По фактическому содержимому файлов:

- HTML-файлы пустые;
- CSS-файл пустой;
- JS-файлы пустые.

То есть это пока только заготовка структуры интерфейса, а не готовый frontend.

## Как запускать

Так как это статический набор файлов, достаточно любого локального static server, например:

```powershell
Set-Location B:\Local_AI_Projeckt\UI_Web
python -m http.server 8088
```

Но до наполнения HTML/JS этот запуск нужен только для дальнейшей разработки.
