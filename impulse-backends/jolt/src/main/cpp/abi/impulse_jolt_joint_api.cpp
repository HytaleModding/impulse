#include "internal/impulse_jolt_joints.h"
#include "internal/impulse_jolt_registry.h"

#include <Jolt/Physics/EActivation.h>
#include <Jolt/Physics/Body/BodyInterface.h>
#include <Jolt/Physics/Body/BodyLockMulti.h>

#include <cstdint>
#include <mutex>

using namespace ImpulseJolt;

extern "C" {

IMPULSE_JOLT_EXPORT std::int64_t impulse_jolt_create_joint(std::int64_t space_handle,
    int joint_type,
    std::int64_t body_a_handle,
    std::int64_t body_b_handle,
    float anchor_ax,
    float anchor_ay,
    float anchor_az,
    float anchor_bx,
    float anchor_by,
    float anchor_bz,
    float axis_x,
    float axis_y,
    float axis_z,
    float rest_length,
    float stiffness,
    float damping,
    float lower_limit,
    float upper_limit,
    int motor_enabled,
    float motor_target_velocity,
    float motor_max_force) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr || body_a_handle == body_b_handle) {
        return 0;
    }
    BodyState* body_a = FindBody(*space, static_cast<std::uint64_t>(body_a_handle));
    BodyState* body_b = FindBody(*space, static_cast<std::uint64_t>(body_b_handle));
    if (body_a == nullptr || body_b == nullptr) {
        return 0;
    }

    JPH::BodyID body_ids[] = {body_a->m_BodyId, body_b->m_BodyId};
    JPH::BodyLockMultiWrite body_locks(space->m_PhysicsSystem.GetBodyLockInterface(),
        body_ids,
        2);
    JPH::Body* locked_body_a = body_locks.GetBody(0);
    JPH::Body* locked_body_b = body_locks.GetBody(1);
    if (locked_body_a == nullptr || locked_body_b == nullptr) {
        return 0;
    }

    JPH::Ref<JPH::Constraint> Constraint = CreateJointConstraint(joint_type,
        *locked_body_a,
        *locked_body_b,
        anchor_ax,
        anchor_ay,
        anchor_az,
        anchor_bx,
        anchor_by,
        anchor_bz,
        axis_x,
        axis_y,
        axis_z,
        rest_length,
        stiffness,
        damping,
        lower_limit,
        upper_limit,
        motor_enabled,
        motor_target_velocity,
        motor_max_force);
    if (Constraint == nullptr) {
        return 0;
    }
    body_locks.ReleaseLocks();

    space->m_PhysicsSystem.AddConstraint(Constraint);
    space->m_PhysicsSystem.GetBodyInterface().ActivateBody(body_a->m_BodyId);
    space->m_PhysicsSystem.GetBodyInterface().ActivateBody(body_b->m_BodyId);

    const std::uint64_t joint_handle = g_NextJointHandle++;
    JointState joint;
    joint.m_Constraint = Constraint;
    joint.m_BodyAHandle = static_cast<std::uint64_t>(body_a_handle);
    joint.m_BodyBHandle = static_cast<std::uint64_t>(body_b_handle);
    joint.m_JointType = joint_type;
    space->m_Joints.emplace(joint_handle, joint);
    return static_cast<std::int64_t>(joint_handle);
}

IMPULSE_JOLT_EXPORT int impulse_jolt_remove_joint(std::int64_t space_handle, std::int64_t joint_handle) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr) {
        return 0;
    }
    space->RemoveJointHandle(static_cast<std::uint64_t>(joint_handle));
    return 1;
}

} // extern "C"
