#include <stdint.h>
#include <stdbool.h>
#include <inb_body.h>
#include <inb_common.h>
#include <inb_space.h>

#ifndef INB_BRIDGE
#define INB_BRIDGE

typedef void (*nb_log_callback)(int level, const char *message);

#ifdef __cplusplus
extern "C"
{
#endif

  /* --- SYSTEM CONTROL --- */
  INB_API void inb_system_init(void);

  /**
   * Registers the java logger.
   *
   * Note: this logger should only be used to log severe errors since it's an expensive upcall.
   *
   * @param callback The memory segment containing the java callback function pointer.
   */
  INB_API void inb_system_set_logger(nb_log_callback callback);

  /**
   * Creates a new space.
   *
   * @return int32t The space ID.
   */
  INB_API int32_t inb_system_create_space(void);

#ifdef __cplusplus
}
#endif
#endif