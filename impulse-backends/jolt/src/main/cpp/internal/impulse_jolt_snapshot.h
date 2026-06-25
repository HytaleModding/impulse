#pragma once

#include "internal/impulse_jolt_space.h"

namespace ImpulseJolt {

void WriteSnapshot(Space& targetSpace, const BodyState& body, float* floats, int* ints);

} // namespace ImpulseJolt
