#pragma once

#include <stdint.h>

#if defined(_WIN32)
#define API __declspec(dllexport)
#else
#define API __attribute__((visibility("default")))
#endif

#if defined(__GNUC__) || defined(__clang__)
#define API_OPTIONAL __attribute__((weak))
#else
#error "Unsupported compiler: This project requires GCC or Clang."
#endif

#include <stdint.h>

#ifdef __cplusplus
extern "C"
{
#endif

#include <stdint.h>

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
  } Quaternion;

  typedef enum
  {
    X = 0,
    Y = 1,
    Z = 2,
  } Axis;

#ifdef __cplusplus
}
#endif