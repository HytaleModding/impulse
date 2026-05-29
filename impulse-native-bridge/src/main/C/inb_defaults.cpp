#include <inb_common.h>

#if defined(__GNUC__) || defined(__clang__)
__attribute__((weak))
#endif
bool inb_space_get_runtime_stats(int32_t space_id, float *output)
{
  return false; // Default: Not implemented
}

#if defined(__GNUC__) || defined(__clang__)
__attribute__((weak))
#endif
bool inb_space_get_step_phase_stats(int32_t space_id, float *output)
{
  return false; // Default: Not implemented
}

#if defined(__GNUC__) || defined(__clang__)
__attribute__((weak))
#endif
void inb_space_reset_step_phase_stats()
{
}
