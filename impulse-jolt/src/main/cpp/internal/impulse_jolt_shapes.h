#pragma once

#include "internal/impulse_jolt_space.h"

#include <Jolt/Physics/Body/MotionType.h>
#include <Jolt/Physics/Collision/Shape/Shape.h>

namespace ImpulseJolt {

JPH::EMotionType MotionType(int bodyType);
JPH::ObjectLayer ObjectLayer(int collisionGroup, int collisionMask);
float CenterOfMassOffsetY(int shapeType,
    float halfExtentY,
    float radius,
    float halfHeight,
    int axis);
JPH::ShapeRefC CreateShape(int shapeType,
    float halfExtentX,
    float halfExtentY,
    float halfExtentZ,
    float radius,
    float halfHeight,
    int axis,
    float groundY);

} // namespace ImpulseJolt
