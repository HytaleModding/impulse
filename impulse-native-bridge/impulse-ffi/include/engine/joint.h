#pragma once

#include <stdint.h>

#include "common.h"

#ifdef __cplusplus
extern "C"
{
#endif

  typedef enum
  {
    eng_JOINT_FIXED = 0,
    eng_JOINT_POINT = 1,
    eng_JOINT_HINGE = 2,
    eng_JOINT_SLIDER = 3,
    eng_JOINT_SPRING = 4
  } eng_JointType;

  /**
   * Retrieves the type of the joint.
   *
   * @param joint_id The ID of the target joint.
   * @return The eng_JointType enum value.
   */
  API eng_JointType eng_joint_get_type(uint32_t space_id, uint64_t joint_id);

  /**
   * Retrieves the ID of the first body (bodyA).
   *
   * @param joint_id The ID of the target joint.
   * @return The ID of bodyA.
   */
  API int64_t eng_joint_get_body_a(uint32_t space_id, uint64_t joint_id);

  /**
   * Retrieves the ID of the second body (bodyB).
   *
   * @param joint_id The ID of the target joint.
   * @return The ID of bodyB.
   */
  API int64_t eng_joint_get_body_b(uint32_t space_id, uint64_t joint_id);

  /**
   * Returns whether the joint is currently enabled.
   *
   * @param joint_id The ID of the target joint.
   * @return True if enabled, false otherwise.
   */
  API bool eng_joint_is_enabled(uint32_t space_id, uint64_t joint_id);

  /**
   * Sets the enabled state of the joint.
   *
   * @param joint_id The ID of the target joint.
   * @param enabled  True to enable, false to disable.
   */
  API void eng_joint_set_enabled(uint32_t space_id, uint64_t joint_id, bool enabled);

  /**
   * Retrieves the anchor on bodyA.
   *
   * @param joint_id   The ID of the target joint.
   * @return Vector3f  The anchor position on bodyA. Returns a zero vector if unavailable.
   */
  API Vector3f eng_joint_get_anchor_a(uint32_t space_id, uint64_t joint_id);

  /**
   * Retrieves the anchor on bodyB.
   *
   * @param joint_id   The ID of the target joint.
   * @return Vector3f  The anchor position on bodyB. Returns a zero vector if unavailable.
   */
  API Vector3f eng_joint_get_anchor_b(uint32_t space_id, uint64_t joint_id);

  /**
   * Retrieves the joint axis.
   *
   * @param joint_id The ID of the target joint.
   * @return Vector3f  The joint axis vector. Returns a zero vector if the joint does not use an axis.
   */
  API Vector3f eng_joint_get_axis(uint32_t space_id, uint64_t joint_id);

  /**
   * Retrieves the lower limit for Hinge and Slider joints.
   *
   * @param joint_id The ID of the target joint.
   * @return The lower limit value.
   */
  API float eng_joint_get_lower_limit(uint32_t space_id, uint64_t joint_id);

  /**
   * Retrieves the upper limit for Hinge and Slider joints.
   *
   * @param joint_id The ID of the target joint.
   * @return The upper limit value.
   */
  API float eng_joint_get_upper_limit(uint32_t space_id, uint64_t joint_id);

  /**
   * Configures the joint limits.
   *
   * @param joint_id   The ID of the target joint.
   * @param lowerLimit The lower limit value.
   * @param upperLimit The upper limit value.
   */
  API void eng_joint_set_limits(uint32_t space_id, uint64_t joint_id, float lowerLimit, float upperLimit);

  /**
   * Returns whether the joint motor is enabled.
   *
   * @param joint_id The ID of the target joint.
   * @return True if motor is enabled, false otherwise.
   */
  API bool eng_joint_is_motor_enabled(uint32_t space_id, uint64_t joint_id);

  /**
   * Enables or disables the joint motor.
   *
   * @param joint_id The ID of the target joint.
   * @param enabled  True to enable, false to disable.
   */
  API void eng_joint_set_motor_enabled(uint32_t space_id, uint64_t joint_id, bool enabled);

  /**
   * Returns the configured target motor velocity.
   *
   * @param joint_id The ID of the target joint.
   * @return The target velocity.
   */
  API float eng_joint_get_motor_target_velocity(uint32_t space_id, uint64_t joint_id);

  /**
   * Returns the configured maximum motor force.
   *
   * @param joint_id The ID of the target joint.
   * @return The maximum force.
   */
  API float eng_joint_get_motor_max_force(uint32_t space_id, uint64_t joint_id);

  /**
   * Sets the motor velocity and maximum force.
   *
   * @param joint_id       The ID of the target joint.
   * @param targetVelocity The desired velocity.
   * @param maxForce       The maximum force.
   */
  API void eng_joint_set_motor(uint32_t space_id, uint64_t joint_id, float targetVelocity, float maxForce);

  /**
   * Returns the spring rest length.
   *
   * @param joint_id The ID of the target joint.
   * @return The rest length, or NaN if not a spring joint.
   */
  API float eng_joint_get_spring_rest_length(uint32_t space_id, uint64_t joint_id);

  /**
   * Returns the spring stiffness.
   *
   * @param joint_id The ID of the target joint.
   * @return The stiffness, or NaN if not a spring joint.
   */
  API float eng_joint_get_spring_stiffness(uint32_t space_id, uint64_t joint_id);

  /**
   * Returns the spring damping.
   *
   * @param joint_id The ID of the target joint.
   * @return The damping, or NaN if not a spring joint.
   */
  API float eng_joint_get_spring_damping(uint32_t space_id, uint64_t joint_id);

#ifdef __cplusplus
}
#endif