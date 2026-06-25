#include "internal/impulse_jolt_snapshot.h"

#include <Jolt/Math/Quat.h>
#include <Jolt/Math/Vec3.h>
#include <Jolt/Physics/Body/BodyInterface.h>
#include <Jolt/Physics/Body/BodyLock.h>
#include <Jolt/Physics/Body/MotionProperties.h>
#include <Jolt/Physics/Body/MotionQuality.h>

namespace ImpulseJolt {

void WriteSnapshot(Space& targetSpace, const BodyState& body, float* floats, int* ints) {
    JPH::BodyInterface& bodyInterface = targetSpace.m_PhysicsSystem.GetBodyInterface();
    JPH::RVec3 position = JPH::RVec3::sZero();
    JPH::Quat rotation = JPH::Quat::sIdentity();
    JPH::Vec3 linearVelocity = JPH::Vec3::sZero();
    JPH::Vec3 angularVelocity = JPH::Vec3::sZero();
    float linearDamping = body.m_LinearDamping;
    float angularDamping = body.m_AngularDamping;

    bodyInterface.GetPositionAndRotation(body.m_BodyId, position, rotation);
    bodyInterface.GetLinearAndAngularVelocity(body.m_BodyId, linearVelocity, angularVelocity);
    JPH::BodyLockRead lock(targetSpace.m_PhysicsSystem.GetBodyLockInterface(), body.m_BodyId);
    if (lock.Succeeded()) {
        const JPH::MotionProperties* motionProperties =
            lock.GetBody().GetMotionPropertiesUnchecked();
        if (motionProperties != nullptr) {
            linearDamping = motionProperties->GetLinearDamping();
            angularDamping = motionProperties->GetAngularDamping();
        }
    }

    floats[0] = static_cast<float>(position.GetX());
    floats[1] = static_cast<float>(position.GetY());
    floats[2] = static_cast<float>(position.GetZ());
    floats[3] = rotation.GetX();
    floats[4] = rotation.GetY();
    floats[5] = rotation.GetZ();
    floats[6] = rotation.GetW();
    floats[7] = linearVelocity.GetX();
    floats[8] = linearVelocity.GetY();
    floats[9] = linearVelocity.GetZ();
    floats[10] = angularVelocity.GetX();
    floats[11] = angularVelocity.GetY();
    floats[12] = angularVelocity.GetZ();
    floats[13] = body.m_Mass;
    floats[14] = bodyInterface.GetFriction(body.m_BodyId);
    floats[15] = bodyInterface.GetRestitution(body.m_BodyId);
    floats[16] = linearDamping;
    floats[17] = angularDamping;
    floats[18] = body.m_CenterOfMassOffsetY;
    floats[19] = body.m_HalfExtentX;
    floats[20] = body.m_HalfExtentY;
    floats[21] = body.m_HalfExtentZ;
    floats[22] = body.m_Radius;
    floats[23] = body.m_HalfHeight;

    ints[0] = body.m_ShapeType;
    ints[1] = body.m_BodyType;
    ints[2] = bodyInterface.IsActive(body.m_BodyId) ? 0 : 1;
    ints[3] = bodyInterface.IsSensor(body.m_BodyId) ? 1 : 0;
    ints[4] = body.m_CollisionGroup;
    ints[5] = body.m_CollisionMask;
    ints[6] = bodyInterface.GetMotionQuality(body.m_BodyId)
            == JPH::EMotionQuality::LinearCast
        ? 1
        : 0;
    ints[7] = body.m_HasBoxHalfExtents ? 1 : 0;
    ints[8] = body.m_Axis;
}

} // namespace ImpulseJolt
