#pragma once

#ifndef COMMON_H
#define COMMON_H

#include <stdint.h>

#if defined(__GNUC__) || defined(__clang__)
#define INB_OPTIONAL __attribute__((weak))
#else
#error "Unsupported compiler: This project requires GCC or Clang."
#endif

#if defined(_WIN32)
#define INB_API __declspec(dllexport)
#else
#define INB_API __attribute__((visibility("default")))
#endif

typedef struct
{
  float x;
  float y;
  float z;
} INB_Vector3f;

typedef struct
{
  float x;
  float y;
  float z;
  float w;
} INB_Quaternionf;

#endif