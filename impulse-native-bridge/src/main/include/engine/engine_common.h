#pragma once

#include <stdint.h>

#if defined(__GNUC__) || defined(__clang__)
#define ENGINE_OPTIONAL __attribute__((weak))
#else
#error "Unsupported compiler: This project requires GCC or Clang."
#endif

#ifdef __cplusplus
extern "C"
{
#endif

  typedef struct
  {
    float x;
    float y;
    float z;
  } Vector3f;

  typedef struct
  {
    float x;
    float y;
    float z;
    float w;
  } Quaternionf;

  typedef enum
  {
    X = 0,
    Y = 1,
    Z = 2,
  } Axis;
#ifdef __cplusplus
}
#endif

#if defined(_WIN32)
#define ENGINE_API __declspec(dllexport)
#else
#define ENGINE_API __attribute__((visibility("default")))
#endif