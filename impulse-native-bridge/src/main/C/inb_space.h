#pragma once
#include <inb_common.h>
#include <stdint.h>

#ifndef INB_SPACE
#define INB_SPACE

// Forces 4-byte alignment.
#pragma pack(push, 4)
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
#pragma pack(pop)

// Forces 4-byte alignment.
#pragma pack(push, 4)
typedef struct
{
  int8_t available;
  uint8_t _padding[3]; // 3 bytes padding to reach 4-byte alignment, no data held.
  int64_t step_nanos;
  int64_t broad_phase_nanos;
  int64_t narrow_phase_nanos;
  int64_t solver_nanos;
  int64_t ccd_nanos;
  int64_t snapshot_nanos;
} INB_StepPhaseStats;
#pragma pack(pop)

// Forces 4-byte alignment.
#pragma pack(push, 4)
typedef struct
{
  int64_t body_id;
  INB_Vector3f point;
  INB_Vector3f normal;
  float fraction;
  float distance;
} INB_RayHit;
#pragma pack(pop)

#ifdef __cplusplus
extern "C"
{
#endif

  /**
   * Executes a single processing tick on the native backend.
   * @param steps Simulation step time slice.
   */
  INB_API void inb_space_step(float steps);

  /**
   * Writes the space gravity on the given memory segment.
   *
   * @param space_id  The ID of the target space.
   * @param output    The memory segment where the gravity should be written.
   */
  INB_API void inb_space_get_gravity(int32_t space_id, INB_Vector3f *output);

  /**
   * Sets the space gravity.
   *
   * @param space_id  The ID of the target space.
   * @param gravity   Pointer to the gravity vector.
   */
  INB_API void inb_space_set_gravity(int32_t space_id, const INB_Vector3f *gravity);

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
   * @param space_id    The ID of the target space.
   * @param half_extent Pointer to the half extent vector.
   * @param mass        The mass of the body.
   * @return The ID of the created physics body.
   */
  INB_API int64_t inb_space_create_box(int32_t space_id, const INB_Vector3f *half_extent, float mass);

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

  /**
   * Performs a closest raycast query between two points.
   * @param space_id  The ID of the target space.
   * @param from      Pointer to the starting Vector3f.
   * @param to        Pointer to the ending Vector3f.
   * @param output    Pointer to the buffer where the hit result will be written.
   * Only modified if the function returns true.
   * @return          True if a hit was found, false otherwise.
   */
  INB_API bool inb_space_raycast_closest(int64_t space_id, const INB_Vector3f *from, const INB_Vector3f *to, INB_RayHit *output);

#ifdef __cplusplus
}
#endif

#endif