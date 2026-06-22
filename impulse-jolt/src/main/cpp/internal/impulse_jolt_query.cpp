#include "internal/impulse_jolt_query.h"
#include "internal/impulse_jolt_registry.h"

#include <Jolt/Math/Vec3.h>
#include <Jolt/Physics/Body/BodyLock.h>
#include <Jolt/Physics/Collision/CastResult.h>

#include <cstdint>

namespace ImpulseJolt {

bool WriteRayHit(Space& targetSpace,
    const JPH::RRayCast& ray,
    const JPH::RayCastResult& hit,
    int index,
    std::int64_t* bodyHandles,
    float* hits) {
    std::uint64_t bodyHandle = NativeHandleForBodyId(targetSpace, hit.mBodyID);
    if (bodyHandle == 0) {
        return false;
    }

    JPH::RVec3 point = ray.GetPointOnRay(hit.mFraction);
    JPH::Vec3 normal = JPH::Vec3::sZero();
    JPH::BodyLockRead lock(targetSpace.m_PhysicsSystem.GetBodyLockInterface(), hit.mBodyID);
    if (lock.Succeeded()) {
        normal = lock.GetBody().GetWorldSpaceSurfaceNormal(hit.mSubShapeID2, point);
    }

    bodyHandles[index] = static_cast<std::int64_t>(bodyHandle);
    int offset = index * RayHitFloatCount;
    hits[offset] = static_cast<float>(point.GetX());
    hits[offset + 1] = static_cast<float>(point.GetY());
    hits[offset + 2] = static_cast<float>(point.GetZ());
    hits[offset + 3] = normal.GetX();
    hits[offset + 4] = normal.GetY();
    hits[offset + 5] = normal.GetZ();
    hits[offset + 6] = hit.mFraction;
    hits[offset + 7] = ray.mDirection.Length() * hit.mFraction;
    return true;
}

} // namespace ImpulseJolt
