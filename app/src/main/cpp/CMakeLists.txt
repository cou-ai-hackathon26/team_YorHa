cmake_minimum_required(VERSION 3.22.1)
project("medidiary_native")

add_library(
    medidiary_native
    SHARED
    native-bridge.cpp
)

find_library(log-lib log)

target_link_libraries(
    medidiary_native
    ${log-lib}
)
