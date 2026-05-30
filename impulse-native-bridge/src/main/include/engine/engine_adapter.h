#pragma once

#include <stdint.h>
#include <stdbool.h>

#include "body.h"
#include "engine_common.h"
#include "space.h"
#include "joint.h"

#ifdef __cplusplus
extern "C"
{
#endif

  /**
   * Creates a new space.
   *
   * @return int32t The space ID.
   */
  ENGINE_API int32_t inb_engine_create_space(void);

  /**
   * Creates a new space with the given ID.
   *
   * @param space_id  The space id to create the space with.
   */
  ENGINE_API void inb_engine_create_space_with_id(int32_t space_id);

#ifdef __cplusplus
}
#endif