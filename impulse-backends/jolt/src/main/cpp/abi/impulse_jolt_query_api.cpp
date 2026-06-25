#include "internal/impulse_jolt_query.h"
#include "internal/impulse_jolt_registry.h"

#include <Jolt/Math/Vec3.h>
#include <Jolt/Physics/Collision/CastResult.h>
#include <Jolt/Physics/Collision/CollisionCollectorImpl.h>
#include <Jolt/Physics/Collision/NarrowPhaseQuery.h>
#include <Jolt/Physics/Collision/RayCast.h>

#include <cstdint>
#include <mutex>

using namespace ImpulseJolt;

extern "C" {

IMPULSE_JOLT_EXPORT int impulse_jolt_raycast_closest(std::int64_t space_handle,
    float from_x,
    float from_y,
    float from_z,
    float to_x,
    float to_y,
    float to_z,
    std::int64_t* body_handles,
    float* hits) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr || body_handles == nullptr || hits == nullptr) {
        return 0;
    }

    JPH::Vec3 direction(to_x - from_x, to_y - from_y, to_z - from_z);
    if (direction.LengthSq() <= 0.0F) {
        return 0;
    }
    JPH::RRayCast ray(JPH::RVec3(from_x, from_y, from_z), direction);
    JPH::RayCastResult hit;
    if (!space->m_PhysicsSystem.GetNarrowPhaseQuery().CastRay(ray, hit)) {
        return 0;
    }
    return WriteRayHit(*space, ray, hit, 0, body_handles, hits) ? 1 : 0;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_raycast_all(std::int64_t space_handle,
    float from_x,
    float from_y,
    float from_z,
    float to_x,
    float to_y,
    float to_z,
    int max_hits,
    std::int64_t* body_handles,
    float* hits) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr || max_hits <= 0 || body_handles == nullptr || hits == nullptr) {
        return 0;
    }

    JPH::Vec3 direction(to_x - from_x, to_y - from_y, to_z - from_z);
    if (direction.LengthSq() <= 0.0F) {
        return 0;
    }

    JPH::RRayCast ray(JPH::RVec3(from_x, from_y, from_z), direction);
    JPH::RayCastSettings settings;
    JPH::ClosestHitPerBodyCollisionCollector<JPH::CastRayCollector> collector;
    space->m_PhysicsSystem.GetNarrowPhaseQuery().CastRay(ray, settings, collector);
    collector.Sort();

    int emitted = 0;
    for (const JPH::RayCastResult& hit : collector.mHits) {
        if (emitted >= max_hits) {
            break;
        }
        if (WriteRayHit(*space, ray, hit, emitted, body_handles, hits)) {
            emitted++;
        }
    }
    return emitted;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_contacts(std::int64_t space_handle,
    int max_contacts,
    std::int64_t* body_handles,
    float* contacts) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr || max_contacts <= 0 || body_handles == nullptr || contacts == nullptr) {
        return 0;
    }

    std::lock_guard<std::mutex> contact_lock(space->m_ContactMutex);
    int emitted = 0;
    for (const ContactRecord& contact : space->m_Contacts) {
        if (emitted >= max_contacts) {
            break;
        }
        int body_offset = emitted * ContactBodyHandleCount;
        body_handles[body_offset] = static_cast<std::int64_t>(contact.m_BodyAHandle);
        body_handles[body_offset + 1] = static_cast<std::int64_t>(contact.m_BodyBHandle);

        int contact_offset = emitted * ContactFloatCount;
        contacts[contact_offset] = contact.m_PointAX;
        contacts[contact_offset + 1] = contact.m_PointAY;
        contacts[contact_offset + 2] = contact.m_PointAZ;
        contacts[contact_offset + 3] = contact.m_PointBX;
        contacts[contact_offset + 4] = contact.m_PointBY;
        contacts[contact_offset + 5] = contact.m_PointBZ;
        contacts[contact_offset + 6] = contact.m_NormalBX;
        contacts[contact_offset + 7] = contact.m_NormalBY;
        contacts[contact_offset + 8] = contact.m_NormalBZ;
        contacts[contact_offset + 9] = contact.m_Distance;
        contacts[contact_offset + 10] = contact.m_Impulse;
        emitted++;
    }
    return emitted;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_contact_count(std::int64_t space_handle) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr) {
        return 0;
    }
    std::lock_guard<std::mutex> contact_lock(space->m_ContactMutex);
    return static_cast<int>(space->m_Contacts.size());
}

} // extern "C"
