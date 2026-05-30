#include "inb_common.h"
#include "inb_space.h"

    INB_OPTIONAL bool inb_space_get_runtime_stats(int32_t space_id, INB_RuntimeStats *output)
    {
      return false; // Default: Not implemented
    }

    INB_OPTIONAL bool inb_space_get_step_phase_stats(int32_t space_id, INB_StepPhaseStats *output)
    {
      return false; // Default: Not implemented
    }

    INB_OPTIONAL void inb_space_reset_step_phase_stats(int32_t space_id)
    {
    }