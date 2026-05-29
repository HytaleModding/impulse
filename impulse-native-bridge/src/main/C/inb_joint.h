#pragma once

#include <inb_common.h>
#include <stdint.h>

#ifndef INB_SPACE
#define INB_SPACE

#ifdef __cplusplus
extern "C"
{
#endif

  typedef enum
  {
    INB_JOINT_FIXED = 0,
    INB_JOINT_POINT = 1,
    INB_JOINT_HINGE = 2,
    INB_JOINT_SLIDER = 3,
    INB_JOINT_SPRING = 4
  } INB_JointType;

  /**
   * Retrieves the type of the joint.
   *
   * @param joint_id The ID of the target joint.
   * @return The INB_JointType enum value.
   */
  INB_API INB_JointType inb_joint_get_type(int64_t joint_id);

  /**
   * Retrieves the ID of the first body (bodyA).
   *
   * @param joint_id The ID of the target joint.
   * @return The ID of bodyA.
   */
  INB_API int64_t inb_joint_get_body_a(int64_t joint_id);

  /**
   * Retrieves the ID of the second body (bodyB).
   *
   * @param joint_id The ID of the target joint.
   * @return The ID of bodyB.
   */
  INB_API int64_t inb_joint_get_body_b(int64_t joint_id);

  /**
   * Returns whether the joint is currently enabled.
   *
   * @param joint_id The ID of the target joint.
   * @return True if enabled, false otherwise.
   */
  INB_API bool inb_joint_is_enabled(int64_t joint_id);

  /**
   * Sets the enabled state of the joint.
   *
   * @param joint_id The ID of the target joint.
   * @param enabled  True to enable, false to disable.
   */
  INB_API void inb_joint_set_enabled(int64_t joint_id, bool enabled);

  /**
   * Retrieves the anchor on bodyA.
   *
   * @param joint_id   The ID of the target joint.
   * @param out_anchor Pointer to the vector to store the result.
   */
  INB_API void inb_joint_get_anchor_a(int64_t joint_id, INB_Vector3f *out_anchor);

  /**
   * Retrieves the anchor on bodyB.
   *
   * @param joint_id   The ID of the target joint.
   * @param out_anchor Pointer to the vector to store the result.
   */
  INB_API void inb_joint_get_anchor_b(int64_t joint_id, INB_Vector3f *out_anchor);

  /**
   * Retrieves the joint axis.
   *
   * @param joint_id The ID of the target joint.
   * @param out_axis Pointer to the vector to store the axis.
   * @return True if the joint supports/uses an axis, false otherwise.
   */
  INB_API bool inb_joint_get_axis(int64_t joint_id, INB_Vector3f *out_axis);

  /**
   * Retrieves the lower limit for Hinge and Slider joints.
   *
   * @param joint_id The ID of the target joint.
   * @return The lower limit value.
   */
  INB_API float inb_joint_get_lower_limit(int64_t joint_id);

  /**
   * Retrieves the upper limit for Hinge and Slider joints.
   *
   * @param joint_id The ID of the target joint.
   * @return The upper limit value.
   */
  INB_API float inb_joint_get_upper_limit(int64_t joint_id);

  /**
   * Configures the joint limits.
   *
   * @param joint_id   The ID of the target joint.
   * @param lowerLimit The lower limit value.
   * @param upperLimit The upper limit value.
   */
  INB_API void inb_joint_set_limits(int64_t joint_id, float lowerLimit, float upperLimit);

  /**
   * Returns whether the joint motor is enabled.
   *
   * @param joint_id The ID of the target joint.
   * @return True if motor is enabled, false otherwise.
   */
  INB_API bool inb_joint_is_motor_enabled(int64_t joint_id);

  /**
   * Enables or disables the joint motor.
   *
   * @param joint_id The ID of the target joint.
   * @param enabled  True to enable, false to disable.
   */
  INB_API void inb_joint_set_motor_enabled(int64_t joint_id, bool enabled);

  /**
   * Returns the configured target motor velocity.
   *
   * @param joint_id The ID of the target joint.
   * @return The target velocity.
   */
  INB_API float inb_joint_get_motor_target_velocity(int64_t joint_id);

  /**
   * Returns the configured maximum motor force.
   *
   * @param joint_id The ID of the target joint.
   * @return The maximum force.
   */
  INB_API float inb_joint_get_motor_max_force(int64_t joint_id);

  /**
   * Sets the motor velocity and maximum force.
   *
   * @param joint_id       The ID of the target joint.
   * @param targetVelocity The desired velocity.
   * @param maxForce       The maximum force.
   */
  INB_API void inb_joint_set_motor(int64_t joint_id, float targetVelocity, float maxForce);

  /**
   * Returns the spring rest length.
   *
   * @param joint_id The ID of the target joint.
   * @return The rest length, or NaN if not a spring joint.
   */
  INB_API float inb_joint_get_spring_rest_length(int64_t joint_id);

  /**
   * Returns the spring stiffness.
   *
   * @param joint_id The ID of the target joint.
   * @return The stiffness, or NaN if not a spring joint.
   */
  INB_API float inb_joint_get_spring_stiffness(int64_t joint_id);

  /**
   * Returns the spring damping.
   *
   * @param joint_id The ID of the target joint.
   * @return The damping, or NaN if not a spring joint.
   */
  INB_API float inb_joint_get_spring_damping(int64_t joint_id);

#ifdef __cplusplus
}
#endif

#endif