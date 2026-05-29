
#include <inb_common.h>
#include <stdint.h>

#ifndef INB_SPACE
#define INB_SPACE

#ifdef __cplusplus
extern "C"
{
#endif

  /**
   * Executes a single processing tick on the native backend using a unified
   * Input/Output byte-stream architecture.
   *
   * @param input_stream      Pointer to the input data buffer (Java -> Native).
   * @param input_bytes       Total size of the input payload in bytes.
   * @param output_stream     Pointer to the poutput buffer (Native -> Java).
   * @param max_output_bytes  Maximum capacity of the allocated output buffer.
   * @param steps             Simulation step time slice.
   * @return int32_t          The actual number of bytes written to the output_stream.
   */
  INB_API int32_t inb_space_step(
      int32_t space_id,
      const uint8_t *input_stream,
      int64_t input_bytes,
      uint8_t *output_stream,
      int64_t max_output_bytes,
      float steps);

  /**
   * Sets the space gravity.
   *
   * @param space_id  The ID of the target space.
   * @param x         X component.
   * @param y         Y component.
   * @param z         Z component.
   */
  INB_API void inb_space_set_gravity(int32_t space_id, float x, float y, float z);

  /**
   * Writes the space gravity on the given memory segment.
   *
   * @param space_id  The ID of the target space.
   * @param output    The memory segment where the gravity should be written.
   */
  INB_API void inb_space_get_gravity(int32_t space_id, float *output);

  /**
   * Removes a body from the specified space.
   *
   * @param space_id  The ID of the space from which to remove the body.
   * @param body_id   The ID of the body to be removed.
   */
  INB_API void inb_space_remove_body(int32_t space_id, int64_t body_id);

  /**
   * Writes the runtimes stats on the given memory segment.
   *
   * @note This function is optional. If not implemented by the user,
   * the system will default to returning false and not write to segment.
   *
   * @param space_id  The ID of the space from which to get the runtime stats.
   * @param output    The memory segment where the runtime stats should be written.
   * @return bool     True if the function is implemented and the memory segment
   *                  contains valid data, false otherwise.
   */
  INB_API bool inb_space_get_runtime_stats(int32_t space_id, int32_t *output);

  /**
   * Writes the phase stats on the given memory segment.
   *
   * @note This function is optional. If not implemented by the user,
   * the system will default to returning false and not write to segment.
   *
   * @param space_id  The ID of the space from which to get the phase stats.
   * @param output    The memory segment where the runtime stats should be written.
   * @return bool     True if the function is implemented and the memory segment
   *                  contains valid data, false otherwise.
   */
  INB_API bool inb_space_get_step_phase_stats(int32_t space_id);

  /**
   * Resets phase counters collected by @see inb_space_runtime_stats().
   *
   * @note This function is optional. If not implemented by the user,
   * the system will default to no-op behaviour.
   *
   * @param space_id  The ID of the space from which to remove the body.
   */
  INB_API void inb_space_reset_step_phase_stats(int32_t space_id);

#ifdef __cplusplus
}
#endif

#endif