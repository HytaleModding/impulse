#pragma once

#include <stdint.h>
#include <assert.h>

#include "engine_common.h"

#ifdef __cplusplus
extern "C"
{
#endif

  /**
   * Executes a single processing tick on the native backend.
   * @param steps Simulation step time slice.
   */
  ENGINE_API void inb_space_step(float steps);

  /**
   * Gets the space gravity vector.
   *
   * @param space_id  The ID of the target space.
   * @return Vector3f The gravity vector for the space.
   */
  ENGINE_API Vector3f inb_space_get_gravity(int32_t space_id);

  /**
   * Sets the space gravity.
   *
   * @param space_id  The ID of the target space.
   * @param gravity   Pointer to the gravity vector.
   */
  ENGINE_API void inb_space_set_gravity(int32_t space_id, const Vector3f *gravity);

  /**
   * Removes a body from the specified space.
   *
   * @param space_id  The ID of the space from which to remove the body.
   * @param body_id   The ID of the body to be removed.
   */
  ENGINE_API void inb_space_remove_body(int32_t space_id, int64_t body_id);

  typedef struct
  {
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
    int8_t available;
  } INB_RuntimeStats;

  /**
   * Gets the runtime statistics for the space.
   *
   * This function returns an INB_RuntimeStats struct. If the backend does not
   * populate runtime stats the returned struct's `available` field will be 0.
   *
   * @param space_id  The ID of the space from which to get the runtime stats.
   * @return INB_RuntimeStats The runtime statistics. Check `available` to verify validity.
   */
  ENGINE_API INB_RuntimeStats inb_space_get_runtime_stats(int32_t space_id);

  typedef struct
  {

    int64_t step_nanos;
    int64_t broad_phase_nanos;
    int64_t narrow_phase_nanos;
    int64_t solver_nanos;
    int64_t ccd_nanos;
    int64_t snapshot_nanos;
    int8_t available;
  } INB_StepPhaseStats;

  static_assert(_Alignof(INB_StepPhaseStats) == 8, "INB_Contact must be 8-byte aligned");

  /**
   * Gets per-step phase timing statistics for the space.
   *
   * Returns an INB_StepPhaseStats struct. If the backend does not populate
   * these stats the returned struct's `available` field will be 0.
   *
   * @param space_id  The ID of the space from which to get the phase stats.
   * @return INB_StepPhaseStats The phase timing statistics. Check `available` to verify validity.
   */
  ENGINE_API INB_StepPhaseStats inb_space_get_step_phase_stats(int32_t space_id);

  /**
   * Resets phase counters collected by @see inb_space_runtime_stats().
   *
   * @note This function is optional. If not implemented by the user,
   * the system will default to no-op behaviour.
   *
   * @param space_id  The ID of the space from which to remove the body.
   */
  ENGINE_API void inb_space_reset_step_phase_stats(int32_t space_id);

  /**
   * Creates a static plane body.
   *
   * @param space_id  The ID of the target space.
   * @param groundY   The Y-coordinate of the ground plane.
   * @return The ID of the created physics body.
   */
  ENGINE_API int64_t inb_space_create_static_plane(int32_t space_id, float groundY);

  /**
   * Creates a box body using dimensions.
   *
   * @param space_id    The ID of the target space.
   * @param half_extent Pointer to the half extent vector.
   * @param mass        The mass of the body.
   * @return The ID of the created physics body.
   */
  ENGINE_API int64_t inb_space_create_box(int32_t space_id, const Vector3f *half_extent, float mass);

  /**
   * Creates a sphere body.
   *
   * @param space_id  The ID of the target space.
   * @param radius    The radius of the sphere.
   * @param mass      The mass of the body.
   * @return The ID of the created physics body.
   */
  ENGINE_API int64_t inb_space_create_sphere(int32_t space_id, float radius, float mass);

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
  ENGINE_API int64_t inb_space_create_capsule(int32_t space_id, float radius, float halfHeight, int32_t axis, float mass);

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
  ENGINE_API int64_t inb_space_create_cylinder(int32_t space_id, float radius, float halfHeight, int32_t axis, float mass);

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
  ENGINE_API int64_t inb_space_create_cone(int32_t space_id, float radius, float halfHeight, int32_t axis, float mass);

  typedef struct
  {
    int64_t body_id;
    Vector3f point;
    Vector3f normal;
    float fraction;
    float distance;
  } RayHit;

  static_assert(_Alignof(RayHit) == 8, "RayHit must be 8-byte aligned");

  /**
   * Performs a closest raycast query between two points.
   *
   * @param space_id  The ID of the target space.
   * @param from      Pointer to the starting Vector3f.
   * @param to        Pointer to the ending Vector3f.
   * @return INB_RayHit The closest hit result. If no hit is found the returned
   *                    struct will have `body_id` set to -1.
   */
  ENGINE_API RayHit inb_space_raycast_closest(int64_t space_id, const Vector3f *from, const Vector3f *to);

  typedef struct
  {
    int64_t bodyA_id;
    int64_t bodyB_id;
    Vector3f pointOnA;
    Vector3f pointOnB;
    Vector3f normalOnB;
    float distance;
    float impulse;
    float _pad;
  } Contact;

  /**
   * Writes the current list of contact to the given memory segment.
   *
   * @param space_id     The ID of the target space.
   * @param output       Pointer to a memory segment buffer where INB_Contact
   *                     structs will be written. Must be large enough to hold
   *                     max_contacts.
   * @param max_contacts The maximum number of contacts to write to the output buffer.
   * @return The actual number of contacts written to the output buffer.
   */
  ENGINE_API int32_t inb_space_get_contacts(int32_t space_id, Contact *output, int32_t max_contacts);

  /**
   * Create a fixed joint.
   * Anchors are local to each body.
   * The joint locks the bodies together with no relative translation or rotation.
   *
   * @param space_id  The ID of the target space.
   * @param bodyA     The ID of the first body.
   * @param bodyB     The ID of the second body.
   * @param anchorA   Pointer to the local anchor on bodyA.
   * @param anchorB   Pointer to the local anchor on bodyB.
   * @return The ID of the created fixed joint.
   */
  ENGINE_API int64_t inb_space_create_fixed_joint(int32_t space_id, int64_t bodyA, int64_t bodyB, const Vector3f *anchorA, const Vector3f *anchorB);

  /**
   * Create a point joint.
   * Anchors are local to each body.
   * The joint keeps the two anchors together but allows free rotation.
   *
   * @param space_id  The ID of the target space.
   * @param bodyA     The ID of the first body.
   * @param bodyB     The ID of the second body.
   * @param anchorA   Pointer to the local anchor on bodyA.
   * @param anchorB   Pointer to the local anchor on bodyB.
   * @return The ID of the created point joint.
   */
  ENGINE_API int64_t inb_space_create_point_joint(int32_t space_id, int64_t bodyA, int64_t bodyB, const Vector3f *anchorA, const Vector3f *anchorB);

  /**
   * Create a hinge joint.
   * Anchors are local to each body.
   * Axis describes the hinge axis in joint local space.
   * The joint allows rotation around that axis.
   *
   * @param space_id  The ID of the target space.
   * @param bodyA     The ID of the first body.
   * @param bodyB     The ID of the second body.
   * @param anchorA   Pointer to the local anchor on bodyA.
   * @param anchorB   Pointer to the local anchor on bodyB.
   * @param axis      Pointer to the hinge axis vector.
   * @return The ID of the created hinge joint.
   */
  ENGINE_API int64_t inb_space_create_hinge_joint(int32_t space_id, int64_t bodyA, int64_t bodyB, const Vector3f *anchorA, const Vector3f *anchorB, const Vector3f *axis);

  /**
   * Create a slider joint.
   * Anchors are local to each body.
   * Axis describes the slide axis in joint local space.
   * The joint allows translation along that axis.
   *
   * @param space_id  The ID of the target space.
   * @param bodyA     The ID of the first body.
   * @param bodyB     The ID of the second body.
   * @param anchorA   Pointer to the local anchor on bodyA.
   * @param anchorB   Pointer to the local anchor on bodyB.
   * @param axis      Pointer to the slide axis vector.
   * @return The ID of the created slider joint.
   */
  ENGINE_API int64_t inb_space_create_slider_joint(int32_t space_id, int64_t bodyA, int64_t bodyB, const Vector3f *anchorA, const Vector3f *anchorB, const Vector3f *axis);

  /**
   * Create a spring joint.
   * Anchors are local to each body.
   * Rest length, stiffness, and damping define the spring behavior.
   *
   * @param space_id  The ID of the target space.
   * @param bodyA     The ID of the first body.
   * @param bodyB     The ID of the second body.
   * @param anchorA   Pointer to the local anchor on bodyA.
   * @param anchorB   Pointer to the local anchor on bodyB.
   * @param restLength The spring rest length.
   * @param stiffness  The spring stiffness coefficient.
   * @param damping    The spring damping coefficient.
   * @return The ID of the created spring joint.
   */
  ENGINE_API int64_t inb_space_create_spring_joint(int32_t space_id, int64_t bodyA, int64_t bodyB, const Vector3f *anchorA, const Vector3f *anchorB, float restLength, float stiffness, float damping);

  /**
   * Removes a joint from the specified space.
   *
   * @param space_id The ID of the space.
   * @param joint_id The ID of the joint to be removed.
   */
  ENGINE_API void inb_space_remove_joint(int32_t space_id, int64_t joint_id);

  /**
   * Returns the number of joints currently present in the specified space.
   *
   * @param space_id The ID of the target space.
   * @return The count of active joints in the space.
   */
  ENGINE_API int32_t inb_space_get_joint_count(int32_t space_id);

  /**
   * Release all backend resources associated with the specified space.
   *
   * @param space_id The ID of the space to destroy.
   */
  ENGINE_API void inb_space_destroy(int32_t space_id);

  /**
   * Signature for the joint iteration callback.
   *
   * @param joint_id  The ID of the joint currently being visited.
   * @param user_data Pointer to user-provided state or context,
   * passed through from the iteration call.
   */
  typedef void (*INB_JointCallback)(int64_t joint_id, void *user_data);

  /**
   * Iterates over all joints in the specified space, invoking the
   * provided callback for each.
   *
   * @note This iteration is read-only. Modifying the joint list
   * (e.g., calling inb_space_remove_joint) during this
   * call is undefined behavior.
   *
   * @param space_id The ID of the target space.
   * @param callback Pointer to the function to execute for each joint.
   * @param user_data Pointer to arbitrary user data to pass to the callback.
   */
  ENGINE_API void inb_space_iterate_joints(int32_t space_id, INB_JointCallback callback, void *user_data);

#ifdef __cplusplus
}
#endif