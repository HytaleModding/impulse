#include <inb_common.h>

INB_OPTIONAL bool inb_space_get_runtime_stats(int32_t space_id, float *output)
{
  return false; // Default: Not implemented
}

INB_OPTIONAL bool inb_space_get_step_phase_stats(int32_t space_id, float *output)
{
  return false; // Default: Not implemented
}

INB_OPTIONAL void inb_space_reset_step_phase_stats()
{
}
