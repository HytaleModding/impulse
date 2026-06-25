#include "internal/impulse_jolt_registry.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <memory>
#include <mutex>

using namespace ImpulseJolt;

extern "C" {

IMPULSE_JOLT_EXPORT std::int64_t impulse_jolt_create_space() {
    EnsureJoltInitialized();
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    const std::uint64_t handle = g_NextSpaceHandle++;
    g_Spaces.emplace(handle, std::make_unique<Space>());
    return static_cast<std::int64_t>(handle);
}

IMPULSE_JOLT_EXPORT int impulse_jolt_destroy_space(std::int64_t space_handle) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    return g_Spaces.erase(static_cast<std::uint64_t>(space_handle)) > 0 ? 1 : 0;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_step(std::int64_t space_handle, float dt) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr || !std::isfinite(dt) || dt <= 0.0F) {
        return 0;
    }
    const int collision_steps = std::max(1, static_cast<int>(std::ceil(dt * 60.0F)));
    space->m_PhysicsSystem.Update(dt,
        collision_steps,
        &space->m_TempAllocator,
        &space->m_JobSystem);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_gravity(std::int64_t space_handle, float x, float y, float z) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr) {
        return 0;
    }
    space->m_PhysicsSystem.SetGravity(JPH::Vec3(x, y, z));
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_get_gravity(std::int64_t space_handle, float* out) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr || out == nullptr) {
        return 0;
    }
    JPH::Vec3 gravity = space->m_PhysicsSystem.GetGravity();
    out[0] = gravity.GetX();
    out[1] = gravity.GetY();
    out[2] = gravity.GetZ();
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_body_count(std::int64_t space_handle) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    return space == nullptr ? 0 : static_cast<int>(space->m_Bodies.size());
}

IMPULSE_JOLT_EXPORT int impulse_jolt_joint_count(std::int64_t space_handle) {
    std::lock_guard<std::mutex> lock(g_RegistryMutex);
    Space* space = FindSpace(static_cast<std::uint64_t>(space_handle));
    return space == nullptr ? 0 : static_cast<int>(space->m_Joints.size());
}

} // extern "C"
