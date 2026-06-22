#pragma once

#include "internal/impulse_jolt_space.h"

#include <Jolt/Physics/Collision/RayCast.h>

#include <cstdint>

namespace ImpulseJolt {

bool WriteRayHit(Space& targetSpace,
    const JPH::RRayCast& ray,
    const JPH::RayCastResult& hit,
    int index,
    std::int64_t* bodyHandles,
    float* hits);

} // namespace ImpulseJolt
