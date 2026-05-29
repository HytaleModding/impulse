#pragma once

#include <stdint.h>
#include <stdbool.h>
#include <inb_body.h>
#include <inb_common.h>
#include <inb_space.h>

#ifndef INB_BRIDGE
#define INB_BRIDGE

#ifdef __cplusplus
extern "C"
{
#endif

  /**
   * Initialize the backend.
   */
  INB_API void inb_backend_init(void);

  typedef void (*nb_log_callback)(int level, const char *message);

  /**
   * Registers the java logger.
   *
   * Note: this logger should only be used to log severe errors since it's an expensive upcall.
   *
   * @param callback The memory segment containing the java callback function pointer.
   */
  INB_API void inb_backend_set_logger(nb_log_callback callback);

  /**
   * Creates a new space.
   *
   * @return int32t The space ID.
   */
  INB_API int32_t inb_backend_create_space(void);

  /**
   * Creates a new space with the given ID.
   *
   * @param space_id  The space id to create the space with.
   */
  INB_API void inb_backend_create_space(int32_t space_id);

#ifdef __cplusplus
}
#endif
#endif