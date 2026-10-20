cmake_minimum_required(VERSION 3.22.1)
project(vaxodpi_native C)

file(GLOB BYEDPI_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/byedpi/*.c")
list(REMOVE_ITEM BYEDPI_SOURCES
    "${CMAKE_CURRENT_SOURCE_DIR}/byedpi/win_service.c")

add_library(byedpi SHARED
    ${BYEDPI_SOURCES}
    native-lib.c
    utils.c
)

target_include_directories(byedpi PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/byedpi"
    "${CMAKE_CURRENT_SOURCE_DIR}"
)

target_compile_options(byedpi PRIVATE
    -std=c99
    -O2
    -D_XOPEN_SOURCE=500
)

target_compile_definitions(byedpi PRIVATE ANDROID_APP)
target_link_libraries(byedpi PRIVATE android log)
