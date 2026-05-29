#pragma once

#include <inb_common.h>
#include <stdint.h>
#include <stdbool.h>

#ifndef INB_BODY
#define INB_BODY

#ifdef __cplusplus
extern "C"
{
#endif

  // --- Transform & State ---

  /**
   * Sets the position of a body in the world.
   * @param body_id  The ID of the target body.
   * @param pos      Pointer to the position vector.
   */
  INB_API void inb_body_set_position(int64_t body_id, const INB_Vector3f *pos);

  /**
   * Writes a body position on the given memory segment.
   * @param body_id  The ID of the target body.
   * @param output   The memory segment where the position should be written.
   */
  INB_API void inb_body_get_position(int64_t body_id, INB_Vector3f *output);

  /**
   * Sets the rotation of a body in the world.
   * @param body_id  The ID of the target body.
   * @param rot      Pointer to the rotation quaternion.
   */
  INB_API void inb_body_set_rotation(int64_t body_id, const INB_Quaternionf *rot);

  /**
   * Writes a body rotation on the given memory segment.
   * @param body_id  The ID of the target body.
   * @param output   The memory segment where the rotation should be written.
   */
  INB_API void inb_body_get_rotation(int64_t body_id, INB_Quaternionf *output);

  /**
   * Sets the linear velocity of a body in the world.
   * @param body_id  The ID of the target body.
   * @param vel      Pointer to the linear velocity vector.
   */
  INB_API void inb_body_set_linear_velocity(int64_t body_id, const INB_Vector3f *vel);

  /**
   * Writes a body linear velocity on the given memory segment.
   * @param body_id  The ID of the target body.
   * @param output   The memory segment where the linear velocity should be written.
   */
  INB_API void inb_body_get_linear_velocity(int64_t body_id, INB_Vector3f *output);

  /**
   * Sets the angular velocity of a body in the world.
   * @param body_id  The ID of the target body.
   * @param vel      Pointer to the angular velocity vector.
   */
  INB_API void inb_body_set_angular_velocity(int64_t body_id, const INB_Vector3f *vel);

  /**
   * Writes a body angular velocity on the given memory segment.
   * @param body_id  The ID of the target body.
   * @param output   The memory segment where the angular velocity should be written.
   */
  INB_API void inb_body_get_angular_velocity(int64_t body_id, INB_Vector3f *output);

  // --- Physical Properties ---

  /**
   * Sets the restitution of a body in the world.
   * @param body_id     The ID of the target body.
   * @param restitution The restitution value.
   */
  INB_API void inb_body_set_restitution(int64_t body_id, float restitution);

  /**
   * Gets the restitution of a body.
   * @param body_id  The ID of the target body.
   * @return float   The body restitution.
   */
  INB_API float inb_body_get_restitution(int64_t body_id);

  /**
   * Sets the friction of a body in the world.
   * @param body_id   The ID of the target body.
   * @param friction  The friction value.
   */
  INB_API void inb_body_set_friction(int64_t body_id, float friction);

  /**
   * Gets the friction of a body.
   * @param body_id  The ID of the target body.
   * @return float   The body friction.
   */
  INB_API float inb_body_get_friction(int64_t body_id);

  /**
   * Sets the mass of a body in the world.
   * @param body_id   The ID of the target body.
   * @param mass      The mass value.
   */
  INB_API void inb_body_set_mass(int64_t body_id, float mass);

  /**
   * Gets the mass of a body.
   * @param body_id  The ID of the target body.
   * @return float   The body mass.
   */
  INB_API float inb_body_get_mass(int64_t body_id);

  /**
   * Sets both the linear and angular damping of a body in the world.
   * @param body_id         The ID of the target body.
   * @param linear_damping  The linear damping value.
   * @param angular_damping The angular damping value.
   */
  INB_API void inb_body_set_damping(int64_t body_id, float linear_damping, float angular_damping);

  typedef enum
  {
    BODY_TYPE_STATIC = 0,
    BODY_TYPE_DYNAMIC = 1,
    BODY_TYPE_KINEMATIC = 2,
  } BodyType;

  /**
   * Sets the body type of a body.
   * @param body_id    The ID of the target body.
   * @param body_type  The body type.
   */
  INB_API void inb_body_set_type(int64_t body_id, BodyType body_type);

  /**
   * Gets the body type of a body.
   * @param body_id    The ID of the target body.
   * @return BodyType  The body type of the body.
   */
  INB_API BodyType inb_body_get_type(int64_t body_id);

  // --- State Checks ---

  INB_API bool inb_body_is_static(int64_t body_id);
  INB_API bool inb_body_is_dynamic(int64_t body_id);
  INB_API bool inb_body_is_kinematic(int64_t body_id);

  /**
   * Sets a body to behave as a sensor.
   * When enabled, the body can overlap other bodies without acting as a solid collider.
   * @param body_id  The ID of the target body.
   * @param sensor   True to enable sensor mode.
   */
  INB_API void inb_body_set_sensor(int64_t body_id, bool sensor);

  /**
   * Checks if a body is a sensor.
   * @param body_id  The ID of the target body.
   * @return bool    True if it is a sensor, false otherwise.
   */
  INB_API bool inb_body_is_sensor(int64_t body_id);

  /**
   * Activates a body.
   * @param body_id  The ID of the target body.
   * @param active   True to activate.
   */
  INB_API void inb_body_set_active(int64_t body_id, bool active);

  /**
   * Checks if a body is active.
   * @param body_id  The ID of the target body.
   * @return bool    True if it is active, false otherwise.
   */
  INB_API bool inb_body_is_active(int64_t body_id);

  /**
   * Puts a body to sleep.
   * @param body_id  The ID of the target body.
   * @param sleep    True to put to sleep.
   */
  INB_API void inb_body_set_sleeping(int64_t body_id, bool sleep);

  /**
   * Checks if a body is sleeping.
   * @param body_id  The ID of the target body.
   * @return bool    True if it is sleeping, false otherwise.
   */
  INB_API bool inb_body_is_sleeping(int64_t body_id);

  // --- Forces & Impulses ---

  /**
   * Applies a force at an offset from a body.
   * @param body_id   The ID of the target body.
   * @param force     The force vector.
   * @param offset    The force offset vector.
   */
  INB_API void inb_body_apply_force(int64_t body_id, const INB_Vector3f *force, const INB_Vector3f *offset);

  /**
   * Applies an impulse at an offset from a body.
   * @param body_id   The ID of the target body.
   * @param impulse   The impulse vector.
   * @param offset    The impulse offset vector.
   */
  INB_API void inb_body_apply_impulse(int64_t body_id, const INB_Vector3f *impulse, const INB_Vector3f *offset);

  /**
   * Applies a torque to a body.
   * @param body_id   The ID of the target body.
   * @param torque    The torque vector.
   * @param is_impulse If true, applies as an impulse.
   */
  INB_API void inb_body_apply_torque(int64_t body_id, const INB_Vector3f *torque, bool is_impulse);

  /**
   * Clears all the forces acting on a body.
   * @param body_id   The ID of the target body.
   */
  INB_API void inb_body_clear_forces(int64_t body_id);

  // --- Collision & Shapes ---

  /**
   * Gets a body collision group.
   * @param body_id   The ID of the target body.
   * @return int32_t  The collision group.
   */
  INB_API int32_t inb_body_get_collision_group(int64_t body_id);

  /**
   * Gets a body collision mask.
   * @param body_id   The ID of the target body.
   * @return int32_t  The collision mask.
   */
  INB_API int32_t inb_body_get_collision_mask(int64_t body_id);

  /**
   * Sets a body continuous collisions status.
   * @param body_id  The ID of the target body.
   * @param enabled  If the continuous collisions should be enabled.
   */
  INB_API void inb_body_set_ccd_enabled(int64_t body_id, bool enabled);

  /**
   * Checks if a body has continuous collision enabled.
   * @param body_id  The ID of the target body.
   * @return bool    True if continuous collision is enabled, false otherwise.
   */
  INB_API bool inb_body_is_ccd_enabled(int64_t body_id);

  typedef enum
  {
    BOX = 0,
    SPHERE = 1,
    CAPSULE = 2,
    CYLINDER = 3,
    CONE = 4,
    PLANE = 5,
    VOXELS = 6,
    UNKNOWN = 7,
  } BodyShapeType;

  /**
   * Gets a body shape type.
   * @param body_id         The ID of the target body.
   * @return BodyShapeType  The body shape type.
   */
  INB_API BodyShapeType inb_body_get_shape_type(int64_t body_id);

  /**
   * Gets a body sphere radius.
   * @param body_id  The ID of the target body.
   * @return float   The body sphere radius.
   */
  INB_API float inb_body_get_sphere_radius(int64_t body_id);

  /**
   * Writes on a memory segment a body box half extents.
   * When the method returns false it means the body is not a box
   * and thus the output memory segment will hold no valid output.
   * @param body_id  The ID of the target body.
   * @param output   The memory segment where the half extents should be written.
   * @return bool    True if the body is a box, false otherwise.
   */
  INB_API bool inb_body_get_box_half_extents(int64_t body_id, INB_Vector3f *output);

  /**
   * Gets a body half height.
   * @param body_id  The ID of the target body.
   * @return float   The body half height.
   */
  INB_API float inb_body_get_half_height(int64_t body_id);

  typedef enum
  {
    X = 0,
    Y = 1,
    Z = 2,
  } Axis;

  /**
   * Gets a body shape axis.
   * @param body_id  The ID of the target body.
   * @return Axis    The body shape axis.
   */
  INB_API Axis inb_body_get_shape_axis(int64_t body_id);

  /**
   * Gets the center of mass Y offset.
   * @param body_id  The ID of the target body.
   * @return float   The Y offset of the center of mass.
   */
  INB_API float inb_body_get_com_y_offset(int64_t body_id);

#ifdef __cplusplus
}
#endif

#endif