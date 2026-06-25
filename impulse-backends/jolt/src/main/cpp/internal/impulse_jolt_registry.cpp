#include "internal/impulse_jolt_registry.h"

#include <Jolt/RegisterTypes.h>
#include <Jolt/Core/Factory.h>
#include <Jolt/Core/Memory.h>

#include <memory>
#include <mutex>
#include <unordered_map>

namespace ImpulseJolt {

std::once_flag s_JoltInitOnce;
std::mutex g_RegistryMutex;
std::uint64_t g_NextSpaceHandle = 1;
std::uint64_t g_NextBodyHandle = 1001;
std::uint64_t g_NextJointHandle = 2001;
std::unordered_map<std::uint64_t, std::unique_ptr<Space>> g_Spaces;

void EnsureJoltInitialized() {
    std::call_once(s_JoltInitOnce, [] {
        JPH::RegisterDefaultAllocator();
        if (JPH::Factory::sInstance == nullptr) {
            JPH::Factory::sInstance = new JPH::Factory();
        }
        JPH::RegisterTypes();
    });
}

Space* FindSpace(std::uint64_t handle) {
    auto iterator = g_Spaces.find(handle);
    return iterator == g_Spaces.end() ? nullptr : iterator->second.get();
}

BodyState* FindBody(Space& targetSpace, std::uint64_t handle) {
    auto iterator = targetSpace.m_Bodies.find(handle);
    return iterator == targetSpace.m_Bodies.end() ? nullptr : &iterator->second;
}

std::uint64_t NativeHandleForBodyId(const Space& targetSpace, const JPH::BodyID& bodyId) {
    auto iterator = targetSpace.m_BodyHandlesByJoltId.find(bodyId.GetIndexAndSequenceNumber());
    return iterator == targetSpace.m_BodyHandlesByJoltId.end() ? 0 : iterator->second;
}

} // namespace ImpulseJolt
