#pragma once

#include <inb_common.h>
#include <stdint.h>
#include <stdbool.h>

#ifndef INB_BODY
#define INB_BODY

typedef enum
{
  BODY_TYPE_STATIC = 0,
  BODY_TYPE_DYNAMIC = 1,
  BODY_TYPE_KINEMATIC = 2,
} BodyType;

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

typedef enum
{
  X = 0,
  Y = 1,
  Z = 2,
} Axis;

#ifdef __cplusplus
extern "C"
{
#endif

  /**
   * Sets the position of a body in the world.
   *
   * @param body_id  The ID of the target body.
   * @param x        X postion.
   * @param y        Y postion.
   * @param z        Z postion.
   */
  INB_API void inb_body_set_position(int64_t body_id, float x, float y, float z);

  /**
   * Writes a body postion on the given memory segment.
   *
   *@param body_id  The ID of the target body.
   * @param output  The memory segment where the position should be written.
   */
  INB_API void inb_body_get_position(int64_t body_id, float *output);

  /**
   *  Sets the rotation of a body in the world.
   *
   * @param body_id  The ID of the target body.
   * @param x        X component.
   * @param y        Y component.
   * @param z        Z component.
   * @param w        Rotation magnitude.
   */
  INB_API void inb_body_set_rotaion(int64_t body_id, float x, float y, float z, float w);

  /**
   * Writes a body rotation on the given memory segment.
   *
   * @param body_id  The ID of the target body.
   * @param output   The memory segment where the rotation should be written.
   */
  INB_API void inb_body_get_rotation(int64_t body_id, float *output);

  /**
   * Sets the linear velocity of a body in the world.
   *
   * @param body_id  The ID of the target body.
   * @param x        X component.
   * @param y        Y component.
   * @param z        Z component.
   */
  INB_API void inb_body_set_linear_velocity(int64_t body_id, float x, float y, float z);

  /**
   * Writes a body lienar velocity on the given memory segment.
   *
   * @param body_id  The ID of the target body.
   * @param output  The memory segment where the linear velocity should be written.
   */
  INB_API void inb_body_get_linear_velocity(int64_t body_id, float *output);

  /**
   * Sets the angular velocity of a body in the world.
   *
   * @param body_id  The ID of the target body.
   * @param x        X component.
   * @param y        Y component.
   * @param z        Z component.
   */
  INB_API void inb_body_set_angular_velocity(int64_t body_id, float x, float y, float z);

  /**
   * Writes a body angular velocity on the given memory segment.
   *
   * @param body_id  The ID of the target body.
   * @param output  The memory segment where the angular velocity should be written.
   */
  INB_API void inb_body_get_angular_velocity(int64_t body_id, float *output);

  /**
   * Sets the restituion of a body in the world.
   *
   * @param body_id     The ID of the target body.
   * @param restituion  The restituion value.
   */
  INB_API void inb_body_set_restituion(int64_t body_id, float restituion);

  /**
   * Gets the restituion of a body.
   *
   * @param body_id  The ID of the target body.
   * @return float   The body restituion.
   */
  INB_API float inb_body_get_restituion(int64_t body_id);

  /**
   * Sets the friction of a body in the world.
   *
   * @param body_id   The ID of the target body.
   * @param friction  The friction value.
   */
  INB_API void inb_body_set_friction(int64_t body_id, float friction);

  /**
   * Gets the riction of a body.
   *
   * @param body_id  The ID of the target body.
   * @return float   The body friction.
   */
  INB_API float inb_body_get_friction(int64_t body_id);

  /**
   * Sets the mass of a body in the world.
   *
   * @param body_id   The ID of the target body.
   * @param mass      The mass value.
   */
  INB_API void inb_body_set_mass(int64_t body_id, float mass);

  /**
   * Gets the mass of a body.
   *
   * @param body_id  The ID of the target body.
   * @return float   The body mass.
   */
  INB_API float inb_body_get_mass(int64_t body_id);

  /**
   * Sets the linear damping of a body in the world.
   *
   * @param body_id         The ID of the target body.
   * @param linear_damping  The linear damping value.
   */
  INB_API void inb_body_set_linear_damping(int64_t body_id, float linear_damping);

  /**
   * Gets the body linear damping.
   *
   * @param body_id  The ID of the target body.
   * @return float   The body linear damping.
   */
  INB_API float inb_body_get_linear_damping(int64_t body_id);

  /**
   * Sets the angular damping of a body in the world.
   *
   * @param body_id          The ID of the target body.
   * @param angular_damping  The angular damping value.
   */
  INB_API void inb_body_set_angular_damping(int64_t body_id, float angular_damping);

  /**
   * Gets the body angular damping.
   *
   * @param body_id  The ID of the target body.
   * @return float   The body angular damping.
   */
  INB_API float inb_body_get_angular_damping(int64_t body_id);

  /**
   * Sets both the linear and angular damping of a body in the world.
   *
   * @param body_id          The ID of the target body.
   * @param linear_damping   The linear damping value.
   * @param angular_damping  The angular damping value.
   */
  INB_API void inb_body_set_angular_damping(int64_t body_id, float linear_damping, float angular_damping);

  /**
   * Sets the body type of a body.
   *
   * @param body_id    The ID of the target body.
   * @param body_type  The body type.
   */
  INB_API void inb_body_set_type(int64_t body_id, BodyType body_type);

  /**
   * Gets the body type of a body.
   *
   * @param body_id    The ID of the target body.
   * @return BodyType  The body type of the body.
   */
  INB_API BodyType inb_body_get_type(int64_t body_id);

  /**
   * Checks if a body body-type is static.
   *
   * @param body_id  The ID of the target body.
   * @return bool    True if the body type is static, false otherwise.
   */
  INB_API bool inb_body_is_static(int64_t body_id);

  /**
   * Checks if a body body-type is dynamic.
   *
   * @param body_id  The ID of the target body.
   * @return bool    True if the body type is dynamic, false otherwise.
   */
  INB_API bool inb_body_is_dynamic(int64_t body_id);

  /**
   * Checks if a body body-type is kinematic.
   *
   * @param body_id  The ID of the target body.
   * @return bool    True if the body type is kinematic, false otherwise.
   */
  INB_API bool inb_body_is_kinematic(int64_t body_id);

  /**
   * Sets a body to behave as a sensor.
   *
   * When enabled, the body can overlap other bodies without acting as a solid collider.
   *
   * @param body_id  The ID of the target body.
   */
  INB_API void inb_body_set_sensor(int64_t body_id);

  /**
   * Checks if a body is a sensor.
   *
   * @param body_id  The ID of the target body.
   * @return bool    True if it is a sensor, flase otherwise.
   */
  INB_API bool inb_body_is_sensor(int64_t body_id);

  /**
   * Activates a body.
   *
   * @param body_id  The ID of the target body.
   */
  INB_API void inb_body_activate(int64_t body_id);

  /**
   * Checks if a body is active.
   *
   * @param body_id  The ID of the target body.
   * @return bool    True if it is active, flase otherwise.
   */
  INB_API bool inb_body_is_active(int64_t body_id);

  /**
   * Puts a body to sleep.
   *
   * @param body_id  The ID of the target body.
   */
  INB_API void inb_body_sleep(int64_t body_id);

  /**
   * Checks if a body is sleeping.
   *
   * @param body_id  The ID of the target body.
   * @return bool    True if it is sleeping, flase otherwise.
   */
  INB_API bool inb_body_is_sleeping(int64_t body_id);

  /**
   * Applies a force at an offset from a body.
   *
   * @param body_id   The ID of the target body.
   * @param force_x   X force component.
   * @param force_y   Y force component.
   * @param force_z   Z force component.
   * @param offset_x  X force offset.
   * @param offset_y  Y force offset.
   * @param offset_z  Z force offset.
   */
  INB_API void inb_body_set_position(int64_t body_id, float force_x, float force_y, float force_z, float offset_x, float offset_y, float offset_z);

  /**
   * Applies an impulse to the center of a body.
   *
   * @param body_id     The ID of the target body.
   * @param impulse_x   X impulse component.
   * @param impulse_y   Y impulse component.
   * @param impulse_z   Z impulse component
   */
  INB_API void inb_body_set_position(int64_t body_id, float impulse_x, float impulse_y, float impulse_z);

  /**
   * Applies an impulse at an offset from a body.
   *
   * @param body_id     The ID of the target body.
   * @param impulse_x   X impulse component.
   * @param impulse_y   Y impulse component.
   * @param impulse_z   Z impulse component.
   * @param offset_x    X impulse offset.
   * @param offset_y    Y impulse offset.
   * @param offset_z    Z impulse offset.
   */
  INB_API void inb_body_set_position(int64_t body_id, float impulse_x, float impulse_y, float impulse_z, float offset_x, float offset_y, float offset_z);

  /**
   * Applies a torque to a body.
   *
   * @param body_id    The ID of the target body.
   * @param torque_x   X torque component.
   * @param torque_y   Y torque component.
   * @param torque_z   Z torque component
   */
  INB_API void inb_body_set_position(int64_t body_id, float torque_x, float torque_y, float torque_z);

  /**
   * Applies a torque impulse to a body.
   *
   * @param body_id        The ID of the target body.
   * @param torque_imp_x   X torque impulse component.
   * @param torque_imp_y   Y torque impulse component.
   * @param torque_imp_z   Z torque impulse component
   */
  INB_API void inb_body_set_position(int64_t body_id, float torque_imp_x, float torque_imp_y, float torque_imp_z);

  /**
   * Clears all the forces acting on a body.
   *
   * @param body_id   The ID of the target body.
   */
  INB_API void inb_body_clear_forces(int64_t body_id);

  /**
   * Gets a body collision group.
   *
   * @param body_id   The ID of the target body.
   * @return int32_t  The collision group.
   */
  INB_API int32_t inb_body_get_collison_group(int64_t body_id);

  /**
   * Gets a body collision mask.
   *
   * @param body_id   The ID of the target body.
   * @return int32_t  The collision mask.
   */
  INB_API int32_t inb_body_get_collison_mask(int64_t body_id);

  /**
   * Sets a body continuos collisions status.
   *
   * @param body_id  The ID of the target body.
   * @param enabled  If the continuos collisions should be enabled.
   */
  INB_API void inb_body_set_continuos_collision_enabled(int64_t body_id, bool enabled);

  /**
   * Checks if a body has continuos collision enabled.
   *
   * @param body_id  The ID of the target body.
   * @return bool    True if continuos collision is enabled, flase otherwise.
   */
  INB_API bool inb_body_is_continuos_collision_enabled(int64_t body_id);

  /**
   * Gets a body shape type.
   *
   * @param body_id         The ID of the target body.
   * @return BodyShapeType  The body shape type.
   */
  INB_API BodyShapeType inb_body_get_shape_type(int64_t body_id);

  /**
   * Gets a body sphere radius.
   *
   * @param body_id  The ID of the target body.
   * @return float   The body sphere radius.
   */
  INB_API float inb_body_get_sphere_radius(int64_t body_id);

  /**
   * Writes on a memory segment a body box half extents.
   *
   * When the method returns false it means the body is not a box
   * and thus the output memory segment will hold no valid output.
   *
   * @param body_id  The ID of the target body.
   * @param output   The memory segment where the half extents should be written.
   * @return bool    True if the body is a box, false otherwise.
   */
  INB_API bool inb_body_get_half_extents(int64_t body_id, float *output);

  /**
   * Gets a body half height.
   *
   * @param body_id  The ID of the target body.
   * @return float   The body half height.
   */
  INB_API float inb_body_get_half_height(int64_t body_id);

  /**
   * Gets a body shape axis.
   *
   * @param body_id  The ID of the target body.
   * @return Axis    The body shape axis.
   */
  INB_API Axis inb_body_get_shape_axis(int64_t body_id);

  /**
   * Gets a the center of mass Y offset.
   *
   * @param body_id  The ID of the target body.
   * @return float   The Y offsett of the center of mass.
   */
  INB_API float inb_body_get_center_of_mass_y_offset(int64_t body_id);

#ifdef __cplusplus
}
#endif

#endif