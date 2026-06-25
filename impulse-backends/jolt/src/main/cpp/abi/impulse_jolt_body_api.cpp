#include "internal/impulse_jolt_registry.h"
#include "internal/impulse_jolt_shapes.h"
#include "internal/impulse_jolt_snapshot.h"

#include <Jolt/Physics/EActivation.h>
#include <Jolt/Physics/Body/BodyCreationSettings.h>
#include <Jolt/Physics/Body/BodyInterface.h>
#include <Jolt/Physics/Body/BodyLock.h>
#include <Jolt/Physics/Body/MotionProperties.h>
#include <Jolt/Physics/Body/MotionQuality.h>

#include <algorithm>
#include <cstdint>
#include <mutex>

using namespace ImpulseJolt;

extern "C" {

IMPULSE_JOLT_EXPORT std::int64_t impulse_jolt_create_body(std::int64_t space_handle,
    int shape_type,
    float half_extent_x,
    float half_extent_y,
    float half_extent_z,
    float radius,
    float half_height,
    int axis,
    float ground_y,
    float mass,
    int body_type,
    float position_x,
    float position_y,
    float position_z,
    float rotation_x,
    float rotation_y,
    float rotation_z,
    float rotation_w) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr) {
        return 0;
    }

    JPH::ShapeRefC shape = CreateShape(shape_type,
        half_extent_x,
        half_extent_y,
        half_extent_z,
        radius,
        half_height,
        axis,
        ground_y);
    if (shape == nullptr) {
        return 0;
    }

    BodyState body;
    body.m_ShapeType = shape_type;
    body.m_BodyType = body_type;
    body.m_Mass = mass;
    body.m_CenterOfMassOffsetY = CenterOfMassOffsetY(shape_type,
        half_extent_y,
        radius,
        half_height,
        axis);
    body.m_HasBoxHalfExtents = shape_type == ShapeBox
        && half_extent_x > 0.0F
        && half_extent_y > 0.0F
        && half_extent_z > 0.0F;
    body.m_HalfExtentX = half_extent_x;
    body.m_HalfExtentY = half_extent_y;
    body.m_HalfExtentZ = half_extent_z;
    body.m_Radius = radius;
    body.m_HalfHeight = half_height;
    body.m_Axis = axis;

    JPH::BodyCreationSettings settings(shape,
        JPH::RVec3(position_x, position_y, position_z),
        JPH::Quat(rotation_x, rotation_y, rotation_z, rotation_w),
        MotionType(body_type),
        ObjectLayer(body.m_CollisionGroup, body.m_CollisionMask));
    settings.mFriction = body.m_Friction;
    settings.mRestitution = body.m_Restitution;
    settings.mLinearDamping = body.m_LinearDamping;
    settings.mAngularDamping = body.m_AngularDamping;
    settings.mAllowDynamicOrKinematic = true;
    settings.mIsSensor = body.m_Sensor;
    if (mass > 0.0F && body_type != BodyStatic) {
        settings.mOverrideMassProperties = JPH::EOverrideMassProperties::CalculateInertia;
        settings.mMassPropertiesOverride.mMass = mass;
    }

    JPH::BodyID BodyId =
        space->m_PhysicsSystem.GetBodyInterface().CreateAndAddBody(settings,
            body_type == BodyStatic
                ? JPH::EActivation::DontActivate
                : JPH::EActivation::Activate);
    if (BodyId.IsInvalid()) {
        return 0;
    }
    body.m_BodyId = BodyId;

    const std::uint64_t body_handle = g_NextBodyHandle++;
    space->m_Bodies.emplace(body_handle, body);
    space->m_BodyHandlesByJoltId.emplace(BodyId.GetIndexAndSequenceNumber(), body_handle);
    return static_cast<std::int64_t>(body_handle);
}

IMPULSE_JOLT_EXPORT int impulse_jolt_remove_body(std::int64_t space_handle, std::int64_t body_handle) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr) {
        return 0;
    }
    auto iterator = space->m_Bodies.find(static_cast<std::uint64_t>(body_handle));
    if (iterator == space->m_Bodies.end()) {
        return 1;
    }
    space->EraseConstraintsForBodyHandle(static_cast<std::uint64_t>(body_handle));
    JPH::BodyInterface& body_interface = space->m_PhysicsSystem.GetBodyInterface();
    if (body_interface.IsAdded(iterator->second.m_BodyId)) {
        body_interface.RemoveBody(iterator->second.m_BodyId);
    }
    body_interface.DestroyBody(iterator->second.m_BodyId);
    space->m_BodyHandlesByJoltId.erase(iterator->second.m_BodyId.GetIndexAndSequenceNumber());
    space->EraseContactRecordsForBodyHandle(static_cast<std::uint64_t>(body_handle));
    space->m_Bodies.erase(iterator);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_contains_body(std::int64_t space_handle, std::int64_t body_handle) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    return space != nullptr
        && FindBody(*space, static_cast<std::uint64_t>(body_handle)) != nullptr
        ? 1
        : 0;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_body_snapshot(std::int64_t space_handle,
    std::int64_t body_handle,
    float* floats,
    int* ints) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr || floats == nullptr || ints == nullptr) {
        return 0;
    }
    BodyState* body = FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    WriteSnapshot(*space, *body, floats, ints);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_transform(std::int64_t space_handle,
    std::int64_t body_handle,
    float position_x,
    float position_y,
    float position_z,
    float rotation_x,
    float rotation_y,
    float rotation_z,
    float rotation_w) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    space->m_PhysicsSystem.GetBodyInterface().SetPositionAndRotation(body->m_BodyId,
        JPH::RVec3(position_x, position_y, position_z),
        JPH::Quat(rotation_x, rotation_y, rotation_z, rotation_w),
        JPH::EActivation::Activate);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_position(std::int64_t space_handle,
    std::int64_t body_handle,
    float x,
    float y,
    float z) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    space->m_PhysicsSystem.GetBodyInterface().SetPosition(body->m_BodyId,
        JPH::RVec3(x, y, z),
        JPH::EActivation::Activate);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_velocity(std::int64_t space_handle,
    std::int64_t body_handle,
    float linear_x,
    float linear_y,
    float linear_z,
    float angular_x,
    float angular_y,
    float angular_z) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    space->m_PhysicsSystem.GetBodyInterface().SetLinearAndAngularVelocity(body->m_BodyId,
        JPH::Vec3(linear_x, linear_y, linear_z),
        JPH::Vec3(angular_x, angular_y, angular_z));
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_type(std::int64_t space_handle, std::int64_t body_handle, int body_type) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->m_BodyType = body_type;
    space->m_PhysicsSystem.GetBodyInterface().SetMotionType(body->m_BodyId,
        MotionType(body_type),
        body_type == BodyStatic ? JPH::EActivation::DontActivate : JPH::EActivation::Activate);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_damping(std::int64_t space_handle,
    std::int64_t body_handle,
    float linear,
    float angular) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->m_LinearDamping = std::max(0.0F, linear);
    body->m_AngularDamping = std::max(0.0F, angular);
    JPH::BodyLockWrite body_lock(space->m_PhysicsSystem.GetBodyLockInterface(), body->m_BodyId);
    if (body_lock.Succeeded()) {
        JPH::MotionProperties* motion_properties =
            body_lock.GetBody().GetMotionPropertiesUnchecked();
        if (motion_properties != nullptr) {
            motion_properties->SetLinearDamping(body->m_LinearDamping);
            motion_properties->SetAngularDamping(body->m_AngularDamping);
        }
    }
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_friction(std::int64_t space_handle,
    std::int64_t body_handle,
    float friction) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->m_Friction = friction;
    space->m_PhysicsSystem.GetBodyInterface().SetFriction(body->m_BodyId, friction);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_restitution(std::int64_t space_handle,
    std::int64_t body_handle,
    float restitution) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->m_Restitution = restitution;
    space->m_PhysicsSystem.GetBodyInterface().SetRestitution(body->m_BodyId, restitution);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_collision_filter(std::int64_t space_handle,
    std::int64_t body_handle,
    int group,
    int mask) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->m_CollisionGroup = group;
    body->m_CollisionMask = mask;
    space->m_PhysicsSystem.GetBodyInterface().SetObjectLayer(body->m_BodyId,
        ObjectLayer(group, mask));
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_sensor(std::int64_t space_handle, std::int64_t body_handle, int sensor) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->m_Sensor = sensor != 0;
    space->m_PhysicsSystem.GetBodyInterface().SetIsSensor(body->m_BodyId, body->m_Sensor);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_continuous_collision(std::int64_t space_handle,
    std::int64_t body_handle,
    int enabled) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->m_ContinuousCollision = enabled != 0;
    space->m_PhysicsSystem.GetBodyInterface().SetMotionQuality(body->m_BodyId,
        body->m_ContinuousCollision
            ? JPH::EMotionQuality::LinearCast
            : JPH::EMotionQuality::Discrete);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_is_body_continuous_collision_enabled(std::int64_t space_handle,
    std::int64_t body_handle) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    return space->m_PhysicsSystem.GetBodyInterface().GetMotionQuality(body->m_BodyId)
            == JPH::EMotionQuality::LinearCast
        ? 1
        : 0;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_activate_body(std::int64_t space_handle, std::int64_t body_handle) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    space->m_PhysicsSystem.GetBodyInterface().ActivateBody(body->m_BodyId);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_sleep_body(std::int64_t space_handle, std::int64_t body_handle) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    space->m_PhysicsSystem.GetBodyInterface().DeactivateBody(body->m_BodyId);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_apply_body_impulse(std::int64_t space_handle,
    std::int64_t body_handle,
    float x,
    float y,
    float z,
    int has_offset,
    float offset_x,
    float offset_y,
    float offset_z,
    int torque) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    JPH::BodyInterface& body_interface = space->m_PhysicsSystem.GetBodyInterface();
    JPH::Vec3 value(x, y, z);
    if (torque != 0) {
        body_interface.AddAngularImpulse(body->m_BodyId, value);
    } else if (has_offset != 0) {
        body_interface.AddImpulse(body->m_BodyId,
            value,
            JPH::RVec3(offset_x, offset_y, offset_z));
    } else {
        body_interface.AddImpulse(body->m_BodyId, value);
    }
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_apply_body_force(std::int64_t space_handle,
    std::int64_t body_handle,
    float x,
    float y,
    float z,
    int has_offset,
    float offset_x,
    float offset_y,
    float offset_z,
    int torque) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : FindBody(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    JPH::BodyInterface& body_interface = space->m_PhysicsSystem.GetBodyInterface();
    JPH::Vec3 value(x, y, z);
    if (torque != 0) {
        body_interface.AddTorque(body->m_BodyId, value, JPH::EActivation::Activate);
    } else if (has_offset != 0) {
        body_interface.AddForce(body->m_BodyId,
            value,
            JPH::RVec3(offset_x, offset_y, offset_z),
            JPH::EActivation::Activate);
    } else {
        body_interface.AddForce(body->m_BodyId, value, JPH::EActivation::Activate);
    }
    return 1;
}

} // extern "C"
