#pragma once

#include "internal/impulse_jolt_space.h"

#include <cstdint>
#include <memory>
#include <mutex>
#include <unordered_map>

namespace ImpulseJolt {

extern std::mutex g_RegistryMutex;
extern std::uint64_t g_NextSpaceHandle;
extern std::uint64_t g_NextBodyHandle;
extern std::uint64_t g_NextJointHandle;
extern std::unordered_map<std::uint64_t, std::unique_ptr<Space>> g_Spaces;

void EnsureJoltInitialized();
Space* FindSpace(std::uint64_t handle);
BodyState* FindBody(Space& targetSpace, std::uint64_t handle);
std::uint64_t NativeHandleForBodyId(const Space& targetSpace, const JPH::BodyID& bodyId);

} // namespace ImpulseJolt
