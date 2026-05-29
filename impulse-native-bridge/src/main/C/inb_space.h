
#include <inb_common.h>
#include <stdint.h>

#ifndef INB_SPACE
#define INB_SPACE

typedef struct
{
  int8_t available;    // Treat as boolean.
  uint8_t _padding[3]; // 3 bytes padding to reach 4-byte alignment, no data held.
  int32_t body_count;
  int32_t collider_count;
  int32_t active_body_count;
  int32_t contact_pair_count;
  int32_t contact_manifold_count;
  int32_t contact_point_count;
  int32_t dynamic_dynamic_contact_pair_count;
  int32_t terrain_contact_pair_count;
  int32_t active_island_count;
  int32_t joint_count;
} INB_RuntimeStats;

typedef struct
{
  int8_t available;
  uint8_t _padding[7]; // 7 bytes padding to reach 8-byte alignment, no data held.
  int64_t step_nanos;
  int64_t broad_phase_nanos;
  int64_t narrow_phase_nanos;
  int64_t solver_nanos;
  int64_t ccd_nanos;
  int64_t snapshot_nanos;
} INB_StepPhaseStats;

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
   * @return int32_t The actual number of bytes written to the output_stream.
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
  INB_API void inb_space_get_gravity(int32_t space_id, INB_Vector3f *output);

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
   * @return bool True if the function is implemented and the memory segment
   *              contains valid data, false otherwise.
   */
  INB_API bool inb_space_get_runtime_stats(int32_t space_id, INB_RuntimeStats *output);

  /**
   * Writes the phase stats on the given memory segment.
   *
   * @note This function is optional. If not implemented by the user,
   * the system will default to returning false and not write to segment.
   *
   * @param space_id  The ID of the space from which to get the phase stats.
   * @param output    The memory segment where the runtime stats should be written.
   * @return bool True if the function is implemented and the memory segment
   *              contains valid data, false otherwise.
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

  /**
   * Creates a static plane body.
   *
   * @param space_id  The ID of the target space.
   * @param groundY   The Y-coordinate of the ground plane.
   * @return The ID of the created physics body.
   */
  INB_API int64_t inb_space_create_static_plane(int32_t space_id, float groundY);

  /**
   * Creates a box body using dimensions.
   *
   * @param space_id  The ID of the target space.
   * @param halfX     Half-extent along the X-axis.
   * @param halfY     Half-extent along the Y-axis.
   * @param halfZ     Half-extent along the Z-axis.
   * @param mass      The mass of the body.
   * @return The ID of the created physics body.
   */
  INB_API int64_t inb_space_create_box(int32_t space_id, float halfX, float halfY, float halfZ, float mass);

  /**
   * Creates a box body using a vector for extents.
   *
   * @param space_id    The ID of the target space.
   * @param halfExtents Pointer to the Vector3f (x, y, z) data.
   * @param mass        The mass of the body.
   * @return The ID of the created physics body.
   */
  INB_API int64_t inb_space_create_box_v(int32_t space_id, const float *halfExtents, float mass);

  /**
   * Creates a sphere body.
   *
   * @param space_id  The ID of the target space.
   * @param radius    The radius of the sphere.
   * @param mass      The mass of the body.
   * @return The ID of the created physics body.
   */
  INB_API int64_t inb_space_create_sphere(int32_t space_id, float radius, float mass);

  /**
   * Creates a capsule body.
   *
   * @param space_id   The ID of the target space.
   * @param radius     The radius of the capsule ends.
   * @param halfHeight The half-height of the capsule cylinder.
   * @param axis       The alignment axis (e.g., 0=X, 1=Y, 2=Z).
   * @param mass       The mass of the body.
   * @return The ID of the created physics body.
   */
  INB_API int64_t inb_space_create_capsule(int32_t space_id, float radius, float halfHeight, int32_t axis, float mass);

  /**
   * Creates a cylinder body.
   *
   * @param space_id   The ID of the target space.
   * @param radius     The radius of the cylinder.
   * @param halfHeight The half-height of the cylinder.
   * @param axis       The alignment axis (e.g., 0=X, 1=Y, 2=Z).
   * @param mass       The mass of the body.
   * @return The ID of the created physics body.
   */
  INB_API int64_t inb_space_create_cylinder(int32_t space_id, float radius, float halfHeight, int32_t axis, float mass);

  /**
   * Creates a cone body.
   *
   * @param space_id   The ID of the target space.
   * @param radius     The radius of the cone base.
   * @param halfHeight The half-height of the cone.
   * @param axis       The alignment axis (e.g., 0=X, 1=Y, 2=Z).
   * @param mass       The mass of the body.
   * @return The ID of the created physics body.
   */
  INB_API int64_t inb_space_create_cone(int32_t space_id, float radius, float halfHeight, int32_t axis, float mass);

#ifdef __cplusplus
}
#endif

#endif