#include "engine/engine_common.h"
#include "engine/space.h"
#include <stdbool.h>

ENGINE_OPTIONAL INB_RuntimeStats inb_space_get_runtime_stats(int32_t space_id)
{
  INB_RuntimeStats stats = {0};
  stats.available = 0; /* Not implemented by default */
  return stats;
}

ENGINE_OPTIONAL INB_StepPhaseStats inb_space_get_step_phase_stats(int32_t space_id)
{
  INB_StepPhaseStats stats = {0};
  stats.available = 0; /* Not implemented by default */
  return stats;
}

ENGINE_OPTIONAL void inb_space_reset_step_phase_stats(int32_t space_id)
{
}