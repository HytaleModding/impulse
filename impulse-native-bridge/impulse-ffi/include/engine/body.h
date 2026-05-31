#pragma once

#include <stdint.h>
#include <stdbool.h>

#include "common.h"

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
  API BodyType eng_body_get_type(uint32_t space_id, uint64_t body_id);

  /**
   * Sets the body type of a body.
   * @param body_id    The ID of the target body.
   * @param body_type  The body type.
   */
  API void eng_body_set_type(uint32_t space_id, uint64_t body_id, BodyType body_type);

  // -------------- Body State ---------------

  API bool eng_body_is_static(uint32_t space_id, uint64_t body_id);
  API bool eng_body_is_dynamic(uint32_t space_id, uint64_t body_id);
  API bool eng_body_is_kinematic(uint32_t space_id, uint64_t body_id);

  // -------------- Position ---------------

  /**
   * Gets the position of a body.
   *
   * @param body_id    The ID of the target body.
   * @return Vector3f  The position vector.
   */
  API Vector3f eng_body_get_position(uint32_t space_id, uint64_t body_id);

  /**
   * Sets the position of a body in the world.
   *
   * @param body_id  The ID of the target body.
   * @param pos      Pointer to the position vector.
   */
  API void eng_body_set_position(uint32_t space_id, uint64_t body_id, Vector3f pos);

  // -------------- Rotation ---------------

  /**
   * Gets the rotation of a body.
   *
   * @param body_id       The ID of the target body.
   * @return Quaternion  The rotation quaternion.
   */
  API Quaternion eng_body_get_rotation(uint32_t space_id, uint64_t body_id);

  /**
   * Sets the rotation of a body in the world.
   *
   * @param body_id  The ID of the target body.
   * @param rot      Pointer to the rotation quaternion.
   */
  API void eng_body_set_rotation(uint32_t space_id, uint64_t body_id, Quaternion rot);

  // -------------- Linear Velocity ---------------

  /**
   * Gets the linear velocity of a body.
   *
   * @param body_id    The ID of the target body.
   * @return Vector3f  The linear velocity vector.
   */
  API Vector3f eng_body_get_linear_velocity(uint32_t space_id, uint64_t body_id);

  /**
   * Sets the linear velocity of a body in the world.
   *
   * @param body_id  The ID of the target body.
   * @param vel      Pointer to the linear velocity vector.
   */
  API void eng_body_set_linear_velocity(uint32_t space_id, uint64_t body_id, Vector3f vel);

  // -------------- Angular Velocity ---------------

  /**
   * Gets the angular velocity of a body.
   *
   * @param body_id    The ID of the target body.
   * @return Vector3f  The angular velocity vector.
   */
  API Vector3f eng_body_get_angular_velocity(uint32_t space_id, uint64_t body_id);

  /**
   * Sets the angular velocity of a body.
   *
   * @param body_id  The ID of the target body.
   * @param vel      Pointer to the angular velocity vector.
   */
  API void eng_body_set_angular_velocity(uint32_t space_id, uint64_t body_id, Vector3f vel);

  // -------------- Restitution ---------------

  /**
   * Gets the restitution of a body.
   * @param body_id  The ID of the target body.
   * @return float   The body restitution.
   */
  API float eng_body_get_restitution(uint32_t space_id, uint64_t body_id);

  /**
   * Sets the restitution of a body in the world.
   * @param body_id     The ID of the target body.
   * @param restitution The restitution value.
   */
  API void eng_body_set_restitution(uint32_t space_id, uint64_t body_id, float restitution);

  // -------------- Friction ---------------

  /**
   * Gets the friction of a body.
   * @param body_id  The ID of the target body.
   * @return float   The body friction.
   */
  API float eng_body_get_friction(uint32_t space_id, uint64_t body_id);

  /**
   * Sets the friction of a body in the world.
   * @param body_id   The ID of the target body.
   * @param friction  The friction value.
   */
  API void eng_body_set_friction(uint32_t space_id, uint64_t body_id, float friction);

  // -------------- Mass ---------------

  /**
   * Gets the mass of a body.
   * @param body_id  The ID of the target body.
   * @return float   The body mass.
   */
  API float eng_body_get_mass(uint32_t space_id, uint64_t body_id);

  /**
   * Sets the mass of a body in the world.
   * @param body_id   The ID of the target body.
   * @param mass      The mass value.
   */
  API void eng_body_set_mass(uint32_t space_id, uint64_t body_id, float mass);

  // -------------- Damping ---------------

  /**
   * Sets both the linear and angular damping of a body in the world.
   * @param body_id         The ID of the target body.
   * @param linear_damping  The linear damping value.
   * @param angular_damping The angular damping value.
   */
  API void eng_body_set_damping(uint32_t space_id, uint64_t body_id, float linear_damping, float angular_damping);

  // -------------- State ---------------

  /**
   * Checks if a body is a sensor.
   * @param body_id  The ID of the target body.
   * @return bool    True if it is a sensor, false otherwise.
   */
  API bool eng_body_is_sensor(uint32_t space_id, uint64_t body_id);

  /**
   * Sets a body to behave as a sensor.
   * When enabled, the body can overlap other bodies without acting as a solid collider.
   * @param body_id  The ID of the target body.
   * @param sensor   True to enable sensor mode.
   */
  API void eng_body_set_sensor(uint32_t space_id, uint64_t body_id, bool sensor);

  /**
   * Checks if a body is active.
   * @param body_id  The ID of the target body.
   * @return bool    True if it is active, false otherwise.
   */
  API bool eng_body_is_active(uint32_t space_id, uint64_t body_id);

  /**
   * Activates a body.
   * @param body_id  The ID of the target body.
   * @param active   True to activate.
   */
  API void eng_body_set_active(uint32_t space_id, uint64_t body_id, bool active);

  /**
   * Checks if a body is sleeping.
   * @param body_id  The ID of the target body.
   * @return bool    True if it is sleeping, false otherwise.
   */
  API bool eng_body_is_sleeping(uint32_t space_id, uint64_t body_id);

  /**
   * Puts a body to sleep.
   * @param body_id  The ID of the target body.
   * @param sleep    True to put to sleep.
   */
  API void eng_body_set_sleeping(uint32_t space_id, uint64_t body_id, bool sleep);

  // ------------- Forces & Impulses ---------------

  /**
   * Applies a force at an offset from a body.
   * @param body_id   The ID of the target body.
   * @param force     The force vector.
   * @param offset    The force offset vector.
   */
  API void eng_body_apply_force(uint32_t space_id, uint64_t body_id, Vector3f force, Vector3f offset);

  /**
   * Applies an impulse at an offset from a body.
   * @param body_id   The ID of the target body.
   * @param impulse   The impulse vector.
   * @param offset    The impulse offset vector.
   */
  API void eng_body_apply_impulse(uint32_t space_id, uint64_t body_id, Vector3f impulse, Vector3f offset);

  /**
   * Applies a torque to a body.
   * @param body_id   The ID of the target body.
   * @param torque    The torque vector.
   * @param is_impulse If true, applies as an impulse.
   */
  API void eng_body_apply_torque(uint32_t space_id, uint64_t body_id, Vector3f torque, bool is_impulse);

  /**
   * Clears all the forces acting on a body.
   * @param body_id   The ID of the target body.
   */
  API void eng_body_clear_forces(uint32_t space_id, uint64_t body_id);

  // ------------- Collision -------------

  /**
   * Gets a body collision group.
   * @param body_id   The ID of the target body.
   * @return int32_t  The collision group.
   */
  API int32_t eng_body_get_collision_group(uint32_t space_id, uint64_t body_id);

  /**
   * Gets a body collision mask.
   * @param body_id   The ID of the target body.
   * @return int32_t  The collision mask.
   */
  API int32_t eng_body_get_collision_mask(uint32_t space_id, uint64_t body_id);

  /**
   * Checks if a body has continuous collision detection enabled.
   * @param body_id  The ID of the target body.
   * @return bool    True if continuous collision detection is enabled, false otherwise.
   */
  API bool eng_body_is_ccd_enabled(uint32_t space_id, uint64_t body_id);

  /**
   * Sets a body's continuous collision detection (CCD) status.
   * @param body_id  The ID of the target body.
   * @param enabled  True to enable continuous collision detection.
   */
  API void eng_body_set_ccd_enabled(uint32_t space_id, uint64_t body_id, bool enabled);

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
  API BodyShapeType eng_body_get_shape_type(uint32_t space_id, uint64_t body_id);

  /**
   * Gets a body sphere radius.
   * @param body_id  The ID of the target body.
   * @return float   The body sphere radius.
   */
  API float eng_body_get_sphere_radius(uint32_t space_id, uint64_t body_id);

  /**
   * Gets the box half extents of a body.
   * Use `inb_body_get_shape_type` to verify the body is a `BOX` before calling.
   * If the body is not a box the function returns a zero vector.
   *
   * @param body_id  The ID of the target body.
   * @return Vector3f  The box half extents vector.
   */
  API Vector3f eng_body_get_box_half_extents(uint32_t space_id, uint64_t body_id);

  /**
   * Gets a body half height.
   * @param body_id  The ID of the target body.
   * @return float   The body half height.
   */
  API float eng_body_get_half_height(uint32_t space_id, uint64_t body_id);

  /**
   * Gets a body shape axis.
   * @param body_id  The ID of the target body.
   * @return Axis    The body shape axis.
   */
  API Axis eng_body_get_shape_axis(uint32_t space_id, uint64_t body_id);

  /**
   * Gets the center of mass Y offset.
   * @param body_id  The ID of the target body.
   * @return float   The Y offset of the center of mass.
   */
  API float eng_body_get_com_y_offset(uint32_t space_id, uint64_t body_id);

#ifdef __cplusplus
}
#endif