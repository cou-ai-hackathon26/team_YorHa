cmake_minimum_required(VERSION 3.22.1)
project("medidiary_llama" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Pinned git submodule (see README: `git submodule update --init --recursive`).
set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)
if(NOT EXISTS ${LLAMA_SRC}/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp sources missing. Run: git submodule update --init --recursive")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(medidiary_llama SHARED llama_jni.cpp)
target_link_libraries(medidiary_llama PRIVATE llama)
if(ANDROID)
    find_library(log-lib log)
    target_link_libraries(medidiary_llama PRIVATE ${log-lib} android)
endif()

# Keep ggml symbols private to this .so so they cannot collide with the ggml copy inside libmedidiary_whisper.so.
target_link_options(medidiary_llama PRIVATE -Wl,--exclude-libs,ALL -Wl,--gc-sections)
target_compile_options(medidiary_llama PRIVATE -O3 -fvisibility=hidden)
