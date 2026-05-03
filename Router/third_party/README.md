# third_party

Vendored-зависимости C++ Router.

## Содержимое

- `httplib.h` - single-header HTTP server/client.
- `json.hpp` - single-header `nlohmann::json`.

## Почему эта папка важна

`CMakeLists.txt` подключает `third_party/` как include directory, поэтому Router собирается без внешнего package manager.

Эта папка не запускается отдельно и не должна содержать бизнес-логику проекта.
