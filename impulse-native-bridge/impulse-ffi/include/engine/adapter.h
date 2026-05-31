#pragma once

#include <stdint.h>
#include <stdbool.h>

#include "common.h"

#ifdef __cplusplus
extern "C"
{
#endif

  /**
   * Creates a new space.
   *
   * @return uint32t The space ID.
   */
  API uint32_t eng_engine_create_space(void);

  /**
   * Creates a new space with the given ID.
   *
   * @param space_id  The space id to create the space with.
   */
  API void eng_engine_create_space_with_id(uint32_t space_id);

#ifdef __cplusplus
}
#endif