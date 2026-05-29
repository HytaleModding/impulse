#pragma once

#ifndef COMMON_H
#define COMMON_H

#include <stdint.h>

#if defined(__GNUC__) || defined(__clang__)
#define INB_OPTIONAL __attribute__((weak))
#else
#define INB_OPTIONAL
#endif

#if defined(_WIN32)
#define INB_API __declspec(dllexport)
#else
#define INB_API __attribute__((visibility("default")))
#endif

#endif