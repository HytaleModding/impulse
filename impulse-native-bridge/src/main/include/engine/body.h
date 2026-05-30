#pragma once

#include <stdint.h>
#include <stdbool.h>

#include "engine_common.h"
#include <common.h>

#ifdef __cplusplus
extern "C"
{
#endif

  // -------------- Body Type ---------------

  typedef enum
  {
    BODY_TYPE_STATIC = 0,
    BODY_TYPE_DYNAMIC = 1,
    BODY_TYPE_KINEMATIC = 2,
  } BodyType;

  /**
   * Gets the body type of a body.
   * @param body_id    The ID of the target body.
   * @return BodyType  The body type of the body.
   */
  ENGINE_API BodyType inb_body_get_type(int64_t body_id);

  /**
   * Sets the body type of a body.
   * @param body_id    The ID of the target body.
   * @param body_type  The body type.
   */
  ENGINE_API void inb_body_set_type(int64_t body_id, BodyType body_type);

  // -------------- Body State ---------------

  ENGINE_API bool inb_body_is_static(int64_t body_id);
  ENGINE_API bool inb_body_is_dynamic(int64_t body_id);
  ENGINE_API bool inb_body_is_kinematic(int64_t body_id);

  // -------------- Position ---------------

  /**
   * Gets the position of a body.
   *
   * @param body_id    The ID of the target body.
   * @return Vector3f  The position vector.
   */
  ENGINE_API Vector3f inb_body_get_position(int64_t body_id);

  /**
   * Sets the position of a body in the world.
   *
   * @param body_id  The ID of the target body.
   * @param pos      Pointer to the position vector.
   */
  ENGINE_API void inb_body_set_position(int64_t body_id, const Vector3f *pos);

  // -------------- Rotation ---------------

  /**
   * Gets the rotation of a body.
   *
   * @param body_id       The ID of the target body.
   * @return Quaternionf  The rotation quaternion.
   */
  ENGINE_API Quaternionf inb_body_get_rotation(int64_t body_id);

  /**
   * Sets the rotation of a body in the world.
   *
   * @param body_id  The ID of the target body.
   * @param rot      Pointer to the rotation quaternion.
   */
  ENGINE_API void inb_body_set_rotation(int64_t body_id, const Quaternionf *rot);

  // -------------- Linear Velocity ---------------

  /**
   * Gets the linear velocity of a body.
   *
   * @param body_id    The ID of the target body.
   * @return Vector3f  The linear velocity vector.
   */
  ENGINE_API Vector3f inb_body_get_linear_velocity(int64_t body_id);

  /**
   * Sets the linear velocity of a body in the world.
   *
   * @param body_id  The ID of the target body.
   * @param vel      Pointer to the linear velocity vector.
   */
  ENGINE_API void inb_body_set_linear_velocity(int64_t body_id, const Vector3f *vel);

  // -------------- Angular Velocity ---------------

  /**
   * Gets the angular velocity of a body.
   *
   * @param body_id    The ID of the target body.
   * @return Vector3f  The angular velocity vector.
   */
  ENGINE_API Vector3f inb_body_get_angular_velocity(int64_t body_id);

  /**
   * Sets the angular velocity of a body.
   *
   * @param body_id  The ID of the target body.
   * @param vel      Pointer to the angular velocity vector.
   */
  ENGINE_API void inb_body_set_angular_velocity(int64_t body_id, const Vector3f *vel);

  // -------------- Restitution ---------------

  /**
   * Gets the restitution of a body.
   * @param body_id  The ID of the target body.
   * @return float   The body restitution.
   */
  ENGINE_API float inb_body_get_restitution(int64_t body_id);

  /**
   * Sets the restitution of a body in the world.
   * @param body_id     The ID of the target body.
   * @param restitution The restitution value.
   */
  ENGINE_API void inb_body_set_restitution(int64_t body_id, float restitution);

  // -------------- Friction ---------------

  /**
   * Gets the friction of a body.
   * @param body_id  The ID of the target body.
   * @return float   The body friction.
   */
  ENGINE_API float inb_body_get_friction(int64_t body_id);

  /**
   * Sets the friction of a body in the world.
   * @param body_id   The ID of the target body.
   * @param friction  The friction value.
   */
  ENGINE_API void inb_body_set_friction(int64_t body_id, float friction);

  // -------------- Mass ---------------

  /**
   * Gets the mass of a body.
   * @param body_id  The ID of the target body.
   * @return float   The body mass.
   */
  ENGINE_API float inb_body_get_mass(int64_t body_id);

  /**
   * Sets the mass of a body in the world.
   * @param body_id   The ID of the target body.
   * @param mass      The mass value.
   */
  ENGINE_API void inb_body_set_mass(int64_t body_id, float mass);

  // -------------- Damping ---------------

  /**
   * Sets both the linear and angular damping of a body in the world.
   * @param body_id         The ID of the target body.
   * @param linear_damping  The linear damping value.
   * @param angular_damping The angular damping value.
   */
  ENGINE_API void inb_body_set_damping(int64_t body_id, float linear_damping, float angular_damping);

  // -------------- State ---------------

  /**
   * Checks if a body is a sensor.
   * @param body_id  The ID of the target body.
   * @return bool    True if it is a sensor, false otherwise.
   */
  ENGINE_API bool inb_body_is_sensor(int64_t body_id);

  /**
   * Sets a body to behave as a sensor.
   * When enabled, the body can overlap other bodies without acting as a solid collider.
   * @param body_id  The ID of the target body.
   * @param sensor   True to enable sensor mode.
   */
  ENGINE_API void inb_body_set_sensor(int64_t body_id, bool sensor);

  /**
   * Checks if a body is active.
   * @param body_id  The ID of the target body.
   * @return bool    True if it is active, false otherwise.
   */
  ENGINE_API bool inb_body_is_active(int64_t body_id);

  /**
   * Activates a body.
   * @param body_id  The ID of the target body.
   * @param active   True to activate.
   */
  ENGINE_API void inb_body_set_active(int64_t body_id, bool active);

  /**
   * Checks if a body is sleeping.
   * @param body_id  The ID of the target body.
   * @return bool    True if it is sleeping, false otherwise.
   */
  ENGINE_API bool inb_body_is_sleeping(int64_t body_id);

  /**
   * Puts a body to sleep.
   * @param body_id  The ID of the target body.
   * @param sleep    True to put to sleep.
   */
  ENGINE_API void inb_body_set_sleeping(int64_t body_id, bool sleep);

  // ------------- Forces & Impulses ---------------

  /**
   * Applies a force at an offset from a body.
   * @param body_id   The ID of the target body.
   * @param force     The force vector.
   * @param offset    The force offset vector.
   */
  ENGINE_API void inb_body_apply_force(int64_t body_id, const Vector3f *force, const Vector3f *offset);

  /**
   * Applies an impulse at an offset from a body.
   * @param body_id   The ID of the target body.
   * @param impulse   The impulse vector.
   * @param offset    The impulse offset vector.
   */
  ENGINE_API void inb_body_apply_impulse(int64_t body_id, const Vector3f *impulse, const Vector3f *offset);

  /**
   * Applies a torque to a body.
   * @param body_id   The ID of the target body.
   * @param torque    The torque vector.
   * @param is_impulse If true, applies as an impulse.
   */
  ENGINE_API void inb_body_apply_torque(int64_t body_id, const Vector3f *torque, bool is_impulse);

  /**
   * Clears all the forces acting on a body.
   * @param body_id   The ID of the target body.
   */
  ENGINE_API void inb_body_clear_forces(int64_t body_id);

  // ------------- Collision -------------

  /**
   * Gets a body collision group.
   * @param body_id   The ID of the target body.
   * @return int32_t  The collision group.
   */
  ENGINE_API int32_t inb_body_get_collision_group(int64_t body_id);

  /**
   * Gets a body collision mask.
   * @param body_id   The ID of the target body.
   * @return int32_t  The collision mask.
   */
  ENGINE_API int32_t inb_body_get_collision_mask(int64_t body_id);

  /**
   * Checks if a body has continuous collision detection enabled.
   * @param body_id  The ID of the target body.
   * @return bool    True if continuous collision detection is enabled, false otherwise.
   */
  ENGINE_API bool inb_body_is_ccd_enabled(int64_t body_id);

  /**
   * Sets a body's continuous collision detection (CCD) status.
   * @param body_id  The ID of the target body.
   * @param enabled  True to enable continuous collision detection.
   */
  ENGINE_API void inb_body_set_ccd_enabled(int64_t body_id, bool enabled);

  // ------------- Shape Information ---------------

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
  ENGINE_API BodyShapeType inb_body_get_shape_type(int64_t body_id);

  /**
   * Gets a body sphere radius.
   * @param body_id  The ID of the target body.
   * @return float   The body sphere radius.
   */
  ENGINE_API float inb_body_get_sphere_radius(int64_t body_id);

  /**
   * Gets the box half extents of a body.
   * Use `inb_body_get_shape_type` to verify the body is a `BOX` before calling.
   * If the body is not a box the function returns a zero vector.
   *
   * @param body_id  The ID of the target body.
   * @return Vector3f  The box half extents vector.
   */
  ENGINE_API Vector3f inb_body_get_box_half_extents(int64_t body_id);

  /**
   * Gets a body half height.
   * @param body_id  The ID of the target body.
   * @return float   The body half height.
   */
  ENGINE_API float inb_body_get_half_height(int64_t body_id);

  /**
   * Gets a body shape axis.
   * @param body_id  The ID of the target body.
   * @return Axis    The body shape axis.
   */
  ENGINE_API Axis inb_body_get_shape_axis(int64_t body_id);

  /**
   * Gets the center of mass Y offset.
   * @param body_id  The ID of the target body.
   * @return float   The Y offset of the center of mass.
   */
  ENGINE_API float inb_body_get_com_y_offset(int64_t body_id);

#ifdef __cplusplus
}
#endif