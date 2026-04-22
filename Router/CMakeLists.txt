cmake_minimum_required(VERSION 3.20)
project(RouterService LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_executable(router_service
    src/main.cpp
)

target_include_directories(router_service PRIVATE third_party)
