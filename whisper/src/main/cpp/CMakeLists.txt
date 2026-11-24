cmake_minimum_required(VERSION 3.22.1)
project("medidiary_whisper" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Pinned git submodule (see README: `git submodule update --init --recursive`).
set(WHISPER_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp)
if(NOT EXISTS ${WHISPER_SRC}/CMakeLists.txt)
    message(FATAL_ERROR "whisper.cpp sources missing. Run: git submodule update --init --recursive")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
add_subdirectory(${WHISPER_SRC} build-whisper)

add_library(medidiary_whisper SHARED whisper_jni.cpp)
target_link_libraries(medidiary_whisper PRIVATE whisper)
if(ANDROID)
    find_library(log-lib log)
    target_link_libraries(medidiary_whisper PRIVATE ${log-lib} android)
endif()

# Keep ggml symbols private to this .so so they cannot collide with the ggml copy inside libmedidiary_llama.so.
target_link_options(medidiary_whisper PRIVATE -Wl,--exclude-libs,ALL -Wl,--gc-sections)
target_compile_options(medidiary_whisper PRIVATE -O3 -fvisibility=hidden)
