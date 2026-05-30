cmake_minimum_required(VERSION 3.10)
project(impulse-native-bridge C)

set(CMAKE_C_STANDARD 11)

add_library(impulse-bridge SHARED src/main/c/inb_defaults.c)

if(NOT CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    message(FATAL_ERROR "This project requires GCC or Clang to compile. Compiler detected: ${CMAKE_CXX_COMPILER_ID}")
endif()

target_include_directories(impulse-bridge PUBLIC
    "${CMAKE_CURRENT_SOURCE_DIR}/src/main/c"
    "${CMAKE_CURRENT_SOURCE_DIR}/src/main/include"
)

target_precompile_headers(impulse-bridge
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/src/main/include/pch.h
)
